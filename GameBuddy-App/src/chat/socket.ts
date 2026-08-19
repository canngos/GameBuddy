import { Client, type IMessage } from '@stomp/stompjs';
import { API_BASE_URL } from '../api/config';

/**
 * What the recipient's socket receives when a message arrives. Mirrors
 * `ChatNotification` on the backend — `message` is plaintext, decrypted for delivery.
 */
export type ChatNotification = {
  id: string;
  senderId: string;
  senderName: string;
  message: string;
};

/** Whether someone is online, and when they were last seen if not. Mirrors `PresenceUpdate`. */
export type PresenceUpdate = {
  userId: string;
  online: boolean;
  /** Null while online, and also null when the server has not seen them since it started. */
  lastSeenAt: string | null;
};

/** Mirrors `TypingNotification` — who is typing, and nothing else. */
export type TypingNotification = { senderId: string };

/**
 * One frame about a lobby, on `/user/queue/lobby`. Mirrors the backend's `LobbyEvent`:
 * a single destination with a discriminator, because the three event types share a
 * lifetime and a consumer — the lobby screen — unlike messages/presence/typing above.
 *
 * - `MESSAGE`: a chat line, in `message`.
 * - `MEMBER`: the roster changed (request, accept, leave, kick) — refetch it.
 * - `STATE`: the lifecycle moved; the new status rides along.
 */
export type LobbyEvent = {
  type: 'MESSAGE' | 'MEMBER' | 'STATE';
  lobbyId: string;
  message: {
    id: string;
    senderId: string;
    senderUsername: string | null;
    message: string;
    date: string;
  } | null;
  status: string | null;
};

/**
 * Gap between keepalive frames. Comfortably inside the server's window — it allows three
 * times the 10s we declare, so two of these can be lost without a disconnection.
 */
const KEEPALIVE_MS = 8000;

export type SocketStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting';

type Listeners = {
  onMessage: (notification: ChatNotification) => void;
  onStatus: (status: SocketStatus) => void;
  onPresence: (update: PresenceUpdate) => void;
  onTyping: (notification: TypingNotification) => void;
  onLobby: (event: LobbyEvent) => void;
};

/**
 * The chat socket. **Receive only** — messages are sent over HTTP, see `chatApi.send`.
 *
 * That split is the point. The backend stores a message before delivering it, so this
 * socket is a latency optimisation and nothing more: when it is down, chat is slower
 * (`useConversation` falls back to polling the history) rather than broken. Nothing here
 * is on the path of *saying* something.
 *
 * **Why plain `/ws`.** The backend used to register that path with SockJS — a
 * compatibility layer for browsers that cannot open a WebSocket, whose transports *all*
 * speak SockJS framing, including the one at `/ws/websocket` that looks like plain
 * WebSocket. A native STOMP client pointed there connects and then hangs: CONNECT is sent,
 * never parsed as STOMP, and nothing replies. It is gone from the backend now; React
 * Native has real WebSocket support and needs no `sockjs-client` (which reaches for
 * browser globals React Native does not have anyway).
 *
 * **Why the token is a native header.** `StompAuthChannelInterceptor` authenticates the
 * CONNECT frame and binds the principal to the session; every later frame is trusted
 * because of it. Before that existed, the sender was read from the client's own JSON and
 * anyone who could open a socket could post as anyone. So the token goes on CONNECT, and
 * a socket without one is refused rather than treated as anonymous.
 */
export function createChatSocket(token: string, listeners: Listeners) {
  const url = brokerUrl();
  if (__DEV__) console.log('[chat] broker', url);

  const client = new Client({
    brokerURL: url,
    // Authenticated once, at CONNECT. See above.
    connectHeaders: { Authorization: `Bearer ${token}` },

    // **Both of these are React Native workarounds, and without them chat never
    // connects at all.**
    //
    // A STOMP frame is terminated by a NULL octet. React Native's WebSocket sends a
    // text frame by handing the JavaScript string to OkHttp on the native side, and the
    // trailing `\0` does not survive that hop — it terminates the string rather than
    // travelling in it. The server therefore receives a frame one byte short: Tomcat
    // logged `byteCount=266` for the 267 bytes stompjs wrote, and Spring's `StompDecoder`
    // answered "Incomplete frame, resetting input buffer".
    //
    // That is the worst possible failure. The socket opens, the sub-protocol negotiates,
    // the bytes arrive — and then the server simply waits for a terminator that is never
    // coming. Nothing errors, nothing closes, nothing is logged at default levels on
    // either side. The UI sat on "connecting" indefinitely.
    //
    // `forceBinaryWSFrames` encodes the frame to a Uint8Array and sends it as a binary
    // frame instead, where length is framing and a NULL is just another byte.
    forceBinaryWSFrames: true,
    // The same hazard in reverse: the broker replies with text frames, and the NULL is
    // stripped again on the way up into JavaScript, so our own parser would never
    // complete an incoming frame. This appends one when the last byte is not already it.
    appendMissingNULLonIncoming: true,

    // Give up on a connection that never completes and let the retry below have a go.
    // The bug above is fixed, but "opened the socket and then heard nothing" is a state
    // a network can produce on its own, and it must not be indistinguishable from
    // working — silence is what made this take so long to find.
    connectionTimeout: 10000,

    // The library reconnects on its own; this is the gap between attempts. Chat that
    // silently stays dead after a tunnel is worse than a brief reconnect.
    reconnectDelay: 4000,

    /**
     * **We promise the server a heartbeat and keep that promise with real frames.**
     *
     * A STOMP heartbeat is a frame containing one newline and nothing else, and React
     * Native's WebSocket mangles exactly that — the same defect as the stripped NULL above,
     * in both directions:
     *
     * - *Incoming*: the server sends them and the device never sees one. Proven with a
     *   plain Node client against this same server, which logs `<<< PONG` every 10s while
     *   the device logs nothing and then declares "did not receive server activity for the
     *   last 20041ms". So `heartbeatIncoming` is 0 — asking for something we cannot receive
     *   made the client close a healthy socket every 20s and reconnect forever, and every
     *   reconnect is a disconnect the server announces, so presence flickered for everyone.
     * - *Outgoing*: stompjs writes its own ping with a direct `send('\n')` rather than
     *   through the frame encoder, so `forceBinaryWSFrames` above does not apply to it. It
     *   goes out as text and is lost, and the server — correctly — closes a client that
     *   promised to speak every 10s and then went quiet. Measured at 33.7s, with an ERROR.
     *
     * `heartbeatOutgoing` stays at 10s anyway, because the *declaration* is what makes the
     * server police us, and being policed is the point: it is the only way the server ever
     * learns that a phone died rather than closed the app cleanly, which is what stops
     * `PresenceRegistry` showing that person online forever.
     *
     * The promise is then kept by `startKeepalive` below rather than by stompjs's own ping.
     * Spring counts any inbound frame as read activity, not only heartbeats — verified: a
     * client sending nothing but ordinary frames survives indefinitely, and one sending
     * nothing at all is cut at 33.7s.
     */
    heartbeatIncoming: 0,
    heartbeatOutgoing: 10000,

    // Only in development. A socket that hangs rather than failing gives no other
    // clue about what it was doing.
    //
    // A no-op in production, never `undefined`. stompjs assigns its own no-op default in
    // the constructor and then copies this configuration over the top, so `undefined`
    // here does not mean "keep the default" — it *replaces* the default with nothing, and
    // every internal `this.debug(...)` throws `undefined is not a function`.
    //
    // The one that matters is inside the connection-timeout watchdog (client.js:443): it
    // fires a few seconds after a socket fails to connect, throws out of a timer where
    // nothing can catch it, and React Native tears the whole instance down. That is a
    // white screen with no error, and it can only happen in a release build — in
    // development `__DEV__` is true and the function exists.
    debug: __DEV__ ? (line) => console.log('[stomp]', line) : () => {},

    onConnect: () => {
      listeners.onStatus('connected');
      startKeepalive();
      // Spring routes `/user/...` per session from the CONNECT principal, so these
      // destinations are private to whoever authenticated — no id in any path.
      //
      // Three queues rather than one multiplexed channel: they have genuinely different
      // lifetimes. A message is a fact worth keeping, presence is current state, and
      // typing is worthless a second later. Sharing a destination would mean every
      // consumer had to switch on a discriminator to ignore most of what it received.
      subscribe('/user/queue/messages', listeners.onMessage);
      subscribe('/user/queue/presence', listeners.onPresence);
      subscribe('/user/queue/typing', listeners.onTyping);
      // Lobby traffic is one multiplexed queue, unlike the three above — its events all
      // feed the same screen, so a discriminator beats a fourth and fifth destination.
      subscribe('/user/queue/lobby', listeners.onLobby);
    },

    onWebSocketClose: () => {
      stopKeepalive();
      // Distinguished from the first connect so the UI can say "reconnecting" rather
      // than showing a spinner that looks like a fresh load.
      listeners.onStatus(client.active ? 'reconnecting' : 'idle');
    },

    // Transport-level failure — refused, DNS, TLS, a dropped tunnel. stompjs retries on
    // its own, so this only reports; without it the failure is completely silent and the
    // screen just says "reconnecting" with no way to find out what is wrong.
    onWebSocketError: (event: Event) => {
      console.warn('[chat] Socket error:', (event as Event & { message?: string }).message ?? event.type);
    },

    onStompError: (frame) => {
      // A STOMP ERROR frame is the server refusing us — a bad or expired token, most
      // likely. Retrying with the same token cannot help, so stop rather than loop.
      console.warn('[chat] Broker refused the connection:', frame.headers.message);
      void client.deactivate();
      listeners.onStatus('idle');
    },
  });

  /**
   * Keeps the heartbeat promise with frames React Native can actually send.
   *
   * Every 8s against a 10s declaration, so a single lost frame is not a disconnection —
   * the server allows three intervals (measured: it cuts a silent client at 33.7s), which
   * leaves room for two consecutive misses.
   *
   * Owned here rather than by the caller because its lifetime is exactly the socket's:
   * started on connect, stopped on close. A timer that outlived the socket would publish
   * into a dead client, and `publish` on a disconnected stompjs throws.
   */
  let keepalive: ReturnType<typeof setInterval> | null = null;

  function startKeepalive() {
    stopKeepalive();
    keepalive = setInterval(() => {
      if (!client.connected) return;
      client.publish({ destination: '/app/chat.keepalive' });
    }, KEEPALIVE_MS);
  }

  function stopKeepalive() {
    if (keepalive) clearInterval(keepalive);
    keepalive = null;
  }

  /** One subscription, with parsing and its failure handling in a single place. */
  function subscribe<T>(destination: string, handle: (payload: T) => void) {
    client.subscribe(destination, (frame: IMessage) => {
      try {
        handle(JSON.parse(frame.body) as T);
      } catch {
        console.warn(`[chat] Could not parse a frame on ${destination}`);
      }
    });
  }

  return {
    connect() {
      listeners.onStatus('connecting');
      client.activate();
    },
    disconnect() {
      stopKeepalive();
      void client.deactivate();
      listeners.onStatus('idle');
    },

    /**
     * Tears the connection down and builds it again, whatever it currently believes.
     *
     * **For coming back from the background, where "connected" cannot be trusted.** The
     * client has no liveness detection of its own — `heartbeatIncoming` is 0 because React
     * Native mangles the newline frames stompjs listens for, so the only thing proving the
     * link is alive is our own keepalive, and Android freezes JS timers while the app is
     * away. The server therefore reaps the session after ~34s of silence and tells everybody
     * this gamer went offline.
     *
     * On the way back, the socket is very often *half-open*: the phone's TCP connection is
     * dead but no close event was ever delivered, so `client.connected` is still true,
     * stompjs's only reconnect trigger (`onWebSocketClose`) never fires, and the gamer stays
     * offline to everyone until the app is killed. That is the bug this exists for.
     *
     * Deactivating first is what makes it unconditional: it also rescues a client that
     * `onStompError` shut down, which leaves `active === false` and is otherwise
     * unrecoverable for the life of the process. Reconnecting a socket that happened to be
     * healthy costs one CONNECT frame; not reconnecting a dead one costs the feature.
     */
    async reconnect() {
      stopKeepalive();
      listeners.onStatus('connecting');
      // Awaited so activate() cannot race the teardown and leave two sockets, which is
      // exactly how the server ends up counting a session that no longer exists.
      await client.deactivate();
      client.activate();
    },

    /**
     * Tells one person that we are typing to them.
     *
     * **Dropped silently when the socket is down**, and that is the whole point of not
     * sending this over HTTP like a message. A typing indicator is only true for the next
     * couple of seconds; queueing it, retrying it, or reporting that it failed would all
     * be effort spent on something that has already stopped being true. The caller does
     * not even find out, because there is nothing it could usefully do about it.
     */
    sendTyping(receiverId: string) {
      if (!client.connected) return;
      client.publish({
        destination: '/app/chat.typing',
        body: JSON.stringify({ receiver: receiverId }),
      });
    },
  };
}

/**
 * `http(s)://host:port` → `ws(s)://host:port/ws`.
 *
 * Derived from the same base URL the REST client uses, so the emulator's loopback
 * rewrite and any `EXPO_PUBLIC_API_URL` override apply here too rather than being
 * duplicated and drifting.
 */
function brokerUrl(): string {
  return `${API_BASE_URL.replace(/^http/, 'ws')}/ws`;
}
