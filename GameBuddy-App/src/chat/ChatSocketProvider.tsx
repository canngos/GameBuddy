import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useSession } from '../session/store';
import {
  createChatSocket,
  type ChatNotification,
  type PresenceUpdate,
  type SocketStatus,
} from './socket';

/** How long a typing indicator stays up after the last keystroke we heard about. */
const TYPING_TTL_MS = 5000;

type MessageHandler = (notification: ChatNotification) => void;

type ChatSocketValue = {
  status: SocketStatus;
  /** Presence by user id, for everyone the server has told us about this session. */
  presence: Record<string, PresenceUpdate>;
  /** User ids currently typing to us. */
  typing: Record<string, true>;
  /** Registers a handler for incoming messages; returns an unsubscribe. */
  onMessage: (handler: MessageHandler) => () => void;
  sendTyping: (receiverId: string) => void;
};

const ChatSocketContext = createContext<ChatSocketValue | null>(null);

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
      onStatus: setStatus,
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

  const value = useMemo<ChatSocketValue>(
    () => ({
      status,
      presence,
      typing,
      onMessage: (handler) => {
        handlers.current.add(handler);
        return () => {
          handlers.current.delete(handler);
        };
      },
      sendTyping: (receiverId) => socket.current?.sendTyping(receiverId),
    }),
    [status, presence, typing],
  );

  return <ChatSocketContext.Provider value={value}>{children}</ChatSocketContext.Provider>;
}

/**
 * Throws rather than returning null when there is no provider.
 *
 * Chat silently doing nothing is the failure mode this whole area has already produced
 * once; a missing provider should be a crash in development, not a screen that looks fine
 * and never receives anything.
 */
export function useChatSocket(): ChatSocketValue {
  const value = useContext(ChatSocketContext);
  if (!value) throw new Error('useChatSocket must be used inside a ChatSocketProvider');
  return value;
}
