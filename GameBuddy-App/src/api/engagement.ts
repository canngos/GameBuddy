import { api } from './client';

/**
 * The Play review ask.
 *
 * A POST, and the name says why: this does not ask a question, it takes a turn. The server
 * checks eligibility and records the ask in one transaction, because Play never reports
 * whether the card was actually shown — see `ReviewPromptService` on the backend.
 */
export const engagementApi = {
  claimReviewPrompt: () => api.post<{ due: boolean }>('/engagement/review-prompt/claim'),
};
