import type { ReasonCode } from '../ui/ReportSheet';
import { api } from './client';

/**
 * Reporting people, and the messages they send.
 *
 * Lived in `communityApi` while community content was reportable; the feature retired and
 * the profile report — the one that was never about communities — stayed. The paths still
 * say `/community` and `/messages` because the backend kept the URLs for installed builds;
 * the moderation queue behind them is its own module now, and a report is evidence in a
 * case rather than a row a moderator ticks.
 */
export const moderationApi = {
  /**
   * Reports a gamer's profile — the picture, the name, what they wrote about themselves.
   * Refused with ALREADY_REPORTED (157) the second time against the same person, so nobody
   * can inflate a case against someone they dislike, and RATE_LIMITED (146) once the daily
   * report budget is spent.
   */
  reportProfile: (userId: string, reasonCode: ReasonCode, note?: string) =>
    api.post<void>(`/community/report/profile/${userId}`, { reasonCode, note }),

  /**
   * Reports a chat message the caller received. Only the recipient may — the backend
   * refuses with RECEIVER_IS_DIFFERENT (143) otherwise, so it is not offered on your own
   * messages. The message and the ten either side become the moderator's context.
   */
  reportMessage: (messageId: string, reasonCode: ReasonCode, note?: string) =>
    api.post<void>(`/messages/report/${messageId}`, { reasonCode, note }),
};
