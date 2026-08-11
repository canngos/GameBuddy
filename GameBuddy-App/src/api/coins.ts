import { api } from './client';
import type { Earn } from './types';

/**
 * Ways to earn coins, as opposed to buying them.
 *
 * Every route answers with the whole earn screen, so a claim leaves the UI correct without
 * a second request — and the new balance comes from the server rather than being inferred
 * by adding a reward to a number that may already have moved.
 */
export const earnApi = {
  state: () => api.get<Earn>('/coins/earn'),
  claimDaily: () => api.post<Earn>('/coins/earn/daily'),
  claimQuest: (code: string) => api.post<Earn>(`/coins/earn/quest/${code}`),
  claimStipend: () => api.post<Earn>('/coins/earn/stipend'),
};
