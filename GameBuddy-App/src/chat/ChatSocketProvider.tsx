import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useSession } from '../session/store';
import {
  createChatSocket,
  type ChatNotification,
  type LobbyEvent,
  type PresenceUpdate,
  type SocketStatus,
} from './socket';

/** How long a typing indicator stays up after the last keystroke we heard about. */
const TYPING_TTL_MS = 5000;

type MessageHandler = (notification: ChatNotification) => void;
type LobbyHandler = (event: LobbyEvent) => void;

/**
 * Subscribing and sending. Never changes for the life of the provider.
 *
 * Split out from the state below because these three are what most consumers actually
 * want, and wanting them should not mean re-rendering on somebody else's keystroke.
 */
type ChatSocketApi = {
  /** Registers a handler for incoming messages; returns an unsubscribe. */
  onMessage: (handler: MessageHandler) => () => void;
  /** Registers a handler for lobby events (chat, roster, lifecycle); returns an unsubscribe. */
  onLobby: (handler: LobbyHandler) => () => void;
  sendTyping: (receiverId: string) => void;
};

/** Connection state. Changes a handful of times per session. */
type ChatSocketStatus = { status: SocketStatus };

/** Who is online and who is typing. Changes constantly, for people all over the app. */
type ChatSocketPresence = {
  /** Presence by user id, for everyone the server has told us about this session. */
  presence: Record<string, PresenceUpdate>;
  /** User ids currently typing to us. */
  typing: Record<string, true>;
};

type ChatSocketValue = ChatSocketApi & ChatSocketStatus & ChatSocketPresence;

/*
 * Three contexts, split by how often each part changes.
 *
 * There was one, memoised on `[status, presence, typing]` — and `presence` is replaced
 * wholesale on every frame the server sends about anyone, anywhere. So one stranger going
 * online re-rendered every consumer of this context, including screens that only ever
 * wanted to *subscribe* to messages and had no interest in presence at all.
 *
 * Now a consumer picks what it depends on and re-renders only for that.
 */
const ApiContext = createContext<ChatSocketApi | null>(null);
const StatusContext = createContext<ChatSocketStatus | null>(null);
const PresenceContext = createContext<ChatSocketPresence | null>(null);

/**
 * One socket, open for as long as somebody is signed in.
 *
 * **It used to be one socket per conversation screen, and that was wrong twice over.**
 *
 * Presence is the reason it had to move. "Online" has to mean "is using GameBuddy"; with
 * the socket tied to the chat screen it would have meant "is looking at this exact
 * conversation", so anyone browsing the deck appeared offline to the person waiting for
 * their reply. There is no way to fix that from the server: the connection simply was not
 * there to observe.
 *
 * The second reason is that live delivery only worked on the screen that happened to be
 * open. Nothing updated the inbox, so a message arriving while you were reading the list
 * showed up whenever the next poll ran. One socket at the top means every screen can be
 * told, and the conversation screen just filters for its own.
 *
 * Presence and typing are held here rather than in `useConversation` because they arrive
 * for people whose screen is not mounted — that is exactly when they are worth keeping.
 */
export function ChatSocketProvider({ children }: { children: ReactNode }) {
  const token = useSession((s) => s.token);

  const [status, setStatus] = useState<SocketStatus>('idle');
  const [presence, setPresence] = useState<Record<string, PresenceUpdate>>({});
  const [typing, setTyping] = useState<Record<string, true>>({});

  /**
   * Handlers in a ref, not state.
   *
   * A screen mounting must not re-create the socket, and putting the handler set in state
   * would do exactly that through the effect below — reconnecting on every navigation,
   * which is also a presence flicker for everybody watching.
   */
  const handlers = useRef(new Set<MessageHandler>());
  const lobbyHandlers = useRef(new Set<LobbyHandler>());
  const socket = useRef<ReturnType<typeof createChatSocket> | null>(null);
  /** One expiry timer per person typing, so a stale indicator cannot get stuck on. */
  const typingTimers = useRef(new Map<string, ReturnType<typeof setTimeout>>());

  useEffect(() => {
    if (!token) {
      setStatus('idle');
      return;
    }

    const timers = typingTimers.current;
    const instance = createChatSocket(token, {
      onStatus: (next) => {
        setStatus(next);
        // Losing the socket invalidates every presence value we are holding, for the same
        // reason the cleanup below clears them: these are pushed updates, and once nobody
        // is pushing, what we have is only a record of what was true when the connection
        // died. That cleanup was the *only* place this happened, and it runs on sign-out
        // and unmount — never on a reconnect, which stompjs handles internally.
        //
        // The cost of not doing it here was the reported bug. Two gamers whose sockets
        // drop together each keep a pushed "offline" for the other; the conversation
        // screen prefers a pushed value over the fresh one it fetches on reconnect, so
        // both stayed greyed out, with their messages arriving normally, until the app was
        // closed and reopened — which ran this cleanup and fixed it.
        //
        // Cleared rather than refetched: with nothing pushed, each screen falls through to
        // its own `GET /presence/{id}`, which is already asked for on reconnect.
        if (next !== 'connected') {
          setPresence({});
          setTyping({});
          timers.forEach(clearTimeout);
          timers.clear();
        }
      },
      onMessage: (notification) => {
        // A message ends the typing indicator: they have stopped typing by definition,
        // and leaving it up next to the thing they just sent looks broken.
        clearTyping(notification.senderId);
        handlers.current.forEach((handler) => handler(notification));
      },
      onPresence: (update) => {
        setPresence((current) => ({ ...current, [update.userId]: update }));
        // Somebody who just went offline is not still typing.
        if (!update.online) clearTyping(update.userId);
      },
      onLobby: (event) => {
        lobbyHandlers.current.forEach((handler) => handler(event));
      },
      onTyping: ({ senderId }) => {
        setTyping((current) => (current[senderId] ? current : { ...current, [senderId]: true }));

        // Restarted on every keystroke, so the indicator follows the typing rather than
        // blinking off every five seconds mid-sentence. Expiry rather than a "stopped"
        // event on purpose — see TypingNotification on the backend.
        clearTimeout(timers.get(senderId));
        timers.set(
          senderId,
          setTimeout(() => clearTyping(senderId), TYPING_TTL_MS),
        );
      },
    });

    function clearTyping(userId: string) {
      clearTimeout(timers.get(userId));
      timers.delete(userId);
      setTyping((current) => {
        if (!current[userId]) return current;
        const next = { ...current };
        delete next[userId];
        return next;
      });
    }

    socket.current = instance;
    instance.connect();

    return () => {
      instance.disconnect();
      socket.current = null;
      timers.forEach(clearTimeout);
      timers.clear();
      // Presence is only true while we are connected to hear about it. Keeping the last
      // known values would leave the app confidently showing people as online after we
      // stopped being told otherwise.
      setPresence({});
      setTyping({});
    };
  }, [token]);

  // Empty deps, and correctly so: all three close over refs, never over state. This object
  // is created once and every consumer that only subscribes or sends can hold it forever.
  const api = useMemo<ChatSocketApi>(
    () => ({
      onMessage: (handler) => {
        handlers.current.add(handler);
        return () => {
          handlers.current.delete(handler);
        };
      },
      onLobby: (handler) => {
        lobbyHandlers.current.add(handler);
        return () => {
          lobbyHandlers.current.delete(handler);
        };
      },
      sendTyping: (receiverId) => socket.current?.sendTyping(receiverId),
    }),
    [],
  );

  const statusValue = useMemo<ChatSocketStatus>(() => ({ status }), [status]);
  const presenceValue = useMemo<ChatSocketPresence>(
    () => ({ presence, typing }),
    [presence, typing],
  );

  return (
    <ApiContext.Provider value={api}>
      <StatusContext.Provider value={statusValue}>
        <PresenceContext.Provider value={presenceValue}>{children}</PresenceContext.Provider>
      </StatusContext.Provider>
    </ApiContext.Provider>
  );
}

/**
 * Throws rather than returning null when there is no provider.
 *
 * Chat silently doing nothing is the failure mode this whole area has already produced
 * once; a missing provider should be a crash in development, not a screen that looks fine
 * and never receives anything.
 */
export function useChatSocket(): ChatSocketValue {
  return { ...useChatSocketApi(), ...useChatSocketStatus(), ...useChatSocketPresence() };
}

/**
 * Subscribe and send, without depending on anything that moves.
 *
 * **Prefer this.** A component using it will not re-render when somebody goes online or
 * starts typing, which is most of what this socket does. `useChatSocket` above is for the
 * conversation screen, which genuinely displays all three.
 */
export function useChatSocketApi(): ChatSocketApi {
  const value = useContext(ApiContext);
  if (!value) throw new Error('useChatSocketApi must be used inside a ChatSocketProvider');
  return value;
}

/** Connection state only. Re-renders a handful of times per session. */
export function useChatSocketStatus(): ChatSocketStatus {
  const value = useContext(StatusContext);
  if (!value) throw new Error('useChatSocketStatus must be used inside a ChatSocketProvider');
  return value;
}

/** Presence and typing. Re-renders on every frame the server sends about anyone. */
export function useChatSocketPresence(): ChatSocketPresence {
  const value = useContext(PresenceContext);
  if (!value) throw new Error('useChatSocketPresence must be used inside a ChatSocketProvider');
  return value;
}
