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

export type SocketStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting';

type Listeners = {
  onMessage: (notification: ChatNotification) => void;
  onStatus: (status: SocketStatus) => void;
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

    // The library reconnects on its own; this is the gap between attempts. Chat that
    // silently stays dead after a tunnel is worse than a brief reconnect.
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    // Only in development. A socket that hangs rather than failing gives no other
    // clue about what it was doing.
    debug: __DEV__ ? (line) => console.log('[stomp]', line) : undefined,

    onConnect: () => {
      listeners.onStatus('connected');
      // Spring routes `/user/...` per session from the CONNECT principal, so this one
      // destination is private to whoever authenticated — no id in the path.
      client.subscribe('/user/queue/messages', (frame: IMessage) => {
        try {
          listeners.onMessage(JSON.parse(frame.body) as ChatNotification);
        } catch {
          console.warn('[chat] Could not parse an incoming frame');
        }
      });
    },

    onWebSocketClose: () => {
      // Distinguished from the first connect so the UI can say "reconnecting" rather
      // than showing a spinner that looks like a fresh load.
      listeners.onStatus(client.active ? 'reconnecting' : 'idle');
    },

    onStompError: (frame) => {
      // A STOMP ERROR frame is the server refusing us — a bad or expired token, most
      // likely. Retrying with the same token cannot help, so stop rather than loop.
      console.warn('[chat] Broker refused the connection:', frame.headers.message);
      void client.deactivate();
      listeners.onStatus('idle');
    },
  });

  return {
    connect() {
      listeners.onStatus('connecting');
      client.activate();
    },
    disconnect() {
      void client.deactivate();
      listeners.onStatus('idle');
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
