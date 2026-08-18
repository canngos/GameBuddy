import { create } from 'zustand';

type ActiveConversationState = {
  /** The friend whose conversation is on screen, or null when none is. */
  friendId: string | null;
  open: (friendId: string) => void;
  close: (friendId: string) => void;
};

/**
 * Which conversation the gamer is looking at right now.
 *
 * **Exists so a message cannot announce itself to the person already reading it.** The
 * backend sends a push for every message including one delivered into an open thread, and
 * it is explicit about why: it cannot know what is on somebody's screen, so the client
 * suppresses the banner for the chat it is already showing. That half was never written,
 * so a reply landed as a bubble and a toast at the same moment — the toast covering the
 * conversation it was announcing.
 *
 * A store rather than reading the route inside the notification listener: the listener
 * depends on nothing that re-renders, and a `usePathname()` there would resubscribe it on
 * every navigation. This is the same shape as `celebration.ts` — one app-wide fact, written
 * by a screen and read by an overlay.
 *
 * `close` takes the id it is closing rather than clearing unconditionally. Moving between
 * two chats mounts the next before the previous is torn down, and a blind clear on the way
 * out would wipe the entry the new screen had just written.
 */
export const useActiveConversation = create<ActiveConversationState>((set, get) => ({
  friendId: null,
  open: (friendId) => set({ friendId }),
  close: (friendId) => {
    if (get().friendId === friendId) set({ friendId: null });
  },
}));
