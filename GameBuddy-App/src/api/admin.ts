import { api } from './client';
import type { Analytics, BlockedUsers, PendingAvatars, Reports } from './types';

/**
 * The moderator console.
 *
 * Every path here is behind `hasRole('ADMIN')` on the server, so an ordinary account
 * calling one gets a 403 rather than an empty list. That matters for how the screens are
 * written: they do not need to check the role before asking, because asking is safe.
 *
 * The endpoints are spread across three server modules — analytics is its own, avatar
 * review belongs to the profile module because it promotes an image between buckets, and
 * reports belong to the community module that owns them. They are gathered here because
 * from the console's side it is one feature.
 */
export const adminApi = {
  /** Every number on the overview screen, in one request. */
  analytics: () => api.get<Analytics>('/admin/analytics'),

  // --- avatar review -------------------------------------------------------

  pendingAvatars: () => api.get<PendingAvatars>('/admin/avatars/pending'),

  approveAvatar: (userId: string) => api.post<{ message: string }>(`/admin/avatars/${userId}/approve`),

  rejectAvatar: (userId: string) => api.post<{ message: string }>(`/admin/avatars/${userId}/reject`),

  /**
   * The image under review, already inlined as a `data:` URI by the server.
   *
   * An ordinary authenticated GET, like everything else here. The first attempt pointed
   * `<Image source={{ uri, headers }}>` at a raw image endpoint, and React Native's image
   * loader dropped the Authorization header on Android: the request reached the backend
   * unauthenticated, the moderator got a blank square, and the only evidence was a 401 in
   * the server log. Nothing about that was visible from the client.
   */
  avatarImage: (userId: string) => api.get<{ image: string }>(`/admin/avatars/${userId}/image`),

  // --- reports -------------------------------------------------------------

  reports: () => api.get<Reports>('/community/admin/reports'),

  /** Removes the reported content and closes every open report against it. */
  actionReport: (reportId: string) =>
    api.post<{ message: string }>(`/community/admin/reports/${reportId}/action`),

  dismissReport: (reportId: string) =>
    api.post<{ message: string }>(`/community/admin/reports/${reportId}/dismiss`),

  // --- accounts ------------------------------------------------------------

  blockedUsers: () => api.get<BlockedUsers>('/admin/get/blocked/users'),

  banUser: (userId: string) => api.post<{ message: string }>(`/admin/ban/user/${userId}`),

  unbanUser: (userId: string) => api.post<{ message: string }>(`/admin/unban/user/${userId}`),
};
