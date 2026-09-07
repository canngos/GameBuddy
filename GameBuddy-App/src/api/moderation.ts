import { api } from './client';

/**
 * Reporting people.
 *
 * Lived in `communityApi` while community content was reportable; the feature retired
 * and the profile report — the one that was never about communities — stayed. The path
 * still says `/community` because the backend kept the URL for installed builds; the
 * moderation queue behind it is its own module now.
 */
export const moderationApi = {
  /**
   * Reports a gamer's profile — the picture, the name, what they wrote about themselves.
   * Refused with ALREADY_REPORTED (157) the second time, so nobody can inflate the count
   * against someone they dislike.
   */
  reportProfile: (userId: string, reason: string) =>
    api.post<void>(`/community/report/profile/${userId}`, { reason }),
};
