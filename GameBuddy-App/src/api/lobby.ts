import { api } from './client';
import type { Lobby, LobbyDetail, LobbyMessage, LobbyTone } from './types';

/** What the backend's `@PageableDefault` uses on `/lobby/browse`. */
export const PAGE_SIZE = 20;

/**
 * Lobbies: an open invitation to play one game, together, at a stated time.
 *
 * Three things shape every screen built on this:
 *
 * 1. **Creating is Gold-only** — `create` answers SUBSCRIPTION_REQUIRED (159) for the
 *    free tier, and the create screen routes that to the paywall. Browsing and asking to
 *    join are free; an owner-screened lobby list only Gold could fill would be empty.
 * 2. **Nobody walks in.** `join` produces a PENDING request the owner answers by hand.
 *    A rejection is final for that lobby (LOBBY_REJECTED, 185); leaving is not.
 * 3. **Chat sends over HTTP, arrivals over the socket** — the same posture as 1:1 chat.
 *    A dropped socket makes lobby chat slower, never broken.
 */
export const lobbyApi = {
  /**
   * OPEN lobbies, soonest first. Every filter is optional and they combine.
   *
   * `startingSoon` is the next 15 minutes — an upper bound, so a lobby whose time has
   * just passed and is still open counts as starting now, which is what somebody
   * looking for a game right this second wants to see.
   */
  browse: (page: number, gameId?: string, tone?: LobbyTone, startingSoon?: boolean) => {
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (gameId) params.set('gameId', gameId);
    if (tone) params.set('tone', tone);
    if (startingSoon) params.set('startingSoon', 'true');
    return api
      .get<{ lobbies: Lobby[] }>(`/lobby/browse?${params.toString()}`)
      .then((d) => d.lobbies ?? []);
  },

  /** Owned + joined + pending requests I sent, `unreadCount` resolved. Not paged. */
  mine: () => api.get<{ lobbies: Lobby[] }>('/lobby/mine').then((d) => d.lobbies ?? []),

  /** LOBBY_NOT_FOUND (178) covers both "archived" and "never existed" — gone is gone. */
  get: (lobbyId: string) => api.get<LobbyDetail>(`/lobby/${lobbyId}`),

  /** Gold only. One live lobby per owner (LOBBY_LIMIT_REACHED, 183). */
  create: (input: {
    gameId: string;
    title: string;
    description?: string;
    requirements?: string;
    tone: LobbyTone;
    maxPlayers: number;
    /** ISO instant; "now" is simply the current time. */
    startsAt: string;
  }) => api.post<LobbyDetail>('/lobby/create', input),

  /** Owner, while OPEN. Every field optional; `maxPlayers` only shrinks to seats filled. */
  update: (lobbyId: string, input: Record<string, unknown>) =>
    api.put<void>(`/lobby/${lobbyId}`, input),

  join: (lobbyId: string) => api.post<void>(`/lobby/${lobbyId}/join`),

  accept: (lobbyId: string, userId: string) =>
    api.post<void>(`/lobby/${lobbyId}/requests/${userId}/accept`),
  reject: (lobbyId: string, userId: string) =>
    api.post<void>(`/lobby/${lobbyId}/requests/${userId}/reject`),

  /** Members and pending requesters both leave this way; owners cancel or end instead. */
  leave: (lobbyId: string) => api.post<void>(`/lobby/${lobbyId}/leave`),
  kick: (lobbyId: string, userId: string) => api.delete<void>(`/lobby/${lobbyId}/kick/${userId}`),

  /**
   * "Team found, stop asking." Quietly rejects the pending queue and takes the lobby out
   * of browse. Starts no timer — the lobby sits LOCKED with its chat open until the owner
   * ends it. Unlock reopens it when somebody bails.
   */
  lock: (lobbyId: string) => api.post<void>(`/lobby/${lobbyId}/lock`),
  unlock: (lobbyId: string) => api.post<void>(`/lobby/${lobbyId}/unlock`),

  /** Owner, from LOCKED. The chat stays readable afterwards. */
  end: (lobbyId: string) => api.post<void>(`/lobby/${lobbyId}/end`),

  /** Owner, from OPEN only — a LOCKED lobby must be unlocked first, by design. */
  cancel: (lobbyId: string) => api.post<void>(`/lobby/${lobbyId}/cancel`),

  /** The history, oldest first. Reading it is what marks the chat read. */
  messages: (lobbyId: string) =>
    api
      .get<{ messages: LobbyMessage[] }>(`/lobby/${lobbyId}/messages`)
      .then((d) => d.messages ?? []),

  /** Returns the message as stored — screened text, not what was typed. */
  send: (lobbyId: string, message: string) =>
    api
      .post<{ message: LobbyMessage }>(`/lobby/${lobbyId}/messages/send`, { message })
      .then((d) => d.message),
};
