import { api } from './client';
import type { Conversation, InboxEntry } from './types';

export const chatApi = {
  /** Who you are talking to, with the last message and an unread count. */
  inbox: () =>
    api
      .get<{ inboxList: InboxEntry[] }>('/messages/get/inbox')
      .then((d) => d.inboxList ?? []),

  /**
   * The full conversation with one gamer.
   *
   * Bodies come back decrypted — they are stored with AES-GCM and decrypted for
   * delivery, never held in plaintext.
   */
  conversation: (friendId: string) =>
    api
      .get<{ conversations: Conversation[] }>(`/messages/get/${friendId}`)
      .then((d) => d.conversations ?? []),

  /**
   * Sends a message.
   *
   * Over HTTP, not the socket, and that is the important part. The message is written
   * to the database before anything is delivered, so the recipient being offline has
   * never mattered — they read it when they next open the conversation. What matters is
   * the *sender's* connection: sending over the socket meant a dropped socket was a dead
   * compose box. An ordinary request works on any connection, can be retried, and fails
   * loudly rather than into a closed channel.
   *
   * Refusals worth handling: NOT_MATCHED (153), USER_BLOCKED (113) and
   * AGE_BAND_MISMATCH (154) — all re-checked on every single message, so a block or an
   * age change takes effect on conversations that are already open.
   */
  send: (receiver: string, message: string) =>
    api.post<void>('/messages/send', { receiver, message }),

  /**
   * Flags a message for moderation. Only the *recipient* may report — the backend
   * refuses with RECEIVER_IS_DIFFERENT (143) otherwise, so this is not offered on
   * your own messages.
   */
  report: (messageId: string) => api.post<void>(`/messages/report/${messageId}`),
};
