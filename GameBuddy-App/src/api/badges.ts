import { api } from './client';
import type { BadgeBoard } from './types';

/**
 * Badges: the board, claiming a reward, and choosing which three to show.
 *
 * Every call returns the whole board rather than an acknowledgement, the same contract
 * the cosmetics store uses and for the same reason: claiming moves the coin balance and
 * that badge's state together, and a screen that has to refetch to learn the second will
 * briefly show a claimed badge next to a balance that has not gone up yet.
 */
export const badgesApi = {
  /**
   * Every mission, earned or not.
   *
   * Not a pure read: the server evaluates the missions while answering, so fetching this
   * is what turns a finished one into an earned badge. Nothing here needs to know that,
   * but it is why the response can contain a badge that was locked a moment ago.
   */
  board: () => api.get<BadgeBoard>('/application/badges'),

  collect: (code: string) => api.post<BadgeBoard>(`/application/badges/${code}/collect`),

  /**
   * Replaces the showcase with these badges, in this order.
   *
   * The whole selection, not one badge at a time — the screen holds three slots and
   * knows what it wants them to be. An empty array takes everything off.
   */
  showcase: (codes: string[]) => api.put<BadgeBoard>('/application/badges/showcase', { codes }),
};
