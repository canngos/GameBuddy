import { create } from 'zustand';
import type { PresenceUpdate } from './socket';

/**
 * Who is online and who is typing, pushed by the socket, selected per person.
 *
 * This lived in `ChatSocketProvider` as React state behind its own context, and that was
 * the right split — until the numbers were counted. The context value is replaced
 * wholesale on every frame the server sends about *anyone*, and its one consumer was the
 * conversation screen: the screen rendering an unbounded message list re-rendered because
 * a stranger elsewhere in the app went online.
 *
 * A store lets that screen subscribe to exactly one person's slice —
 * `useChatPresence((s) => s.presence[friendId])` — and re-render only when that person
 * changes. The provider stays the only writer; the socket callbacks write here instead of
 * calling `setState` on the provider component.
 */
export const useChatPresence = create<{
  /** Presence by user id, for everyone the server has told us about this session. */
  presence: Record<string, PresenceUpdate>;
  /** User ids currently typing to us. */
  typing: Record<string, true>;
}>(() => ({ presence: {}, typing: {} }));
