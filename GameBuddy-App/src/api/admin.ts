import { api } from './client';
import type {
  Analytics,
  BlockedUsers,
  DirectoryFilter,
  PendingAvatars,
  PromoCode,
  PromoCodeInput,
  PromoCodes,
  Reports,
  UserDirectory,
  UserIds,
} from './types';

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

  // --- promotion codes -----------------------------------------------------

  promoCodes: () => api.get<PromoCodes>('/admin/promo-codes'),

  /** One code with its recipient list; the list endpoint sends counts instead. */
  promoCode: (id: string) => api.get<PromoCode>(`/admin/promo-codes/${id}`),

  createPromoCode: (body: PromoCodeInput) => api.post<PromoCode>('/admin/promo-codes', body),

  /**
   * Edits everything except the code string, which the server ignores if it is sent.
   * Renaming a code would break the one already sitting in somebody's inbox.
   */
  updatePromoCode: (id: string, body: PromoCodeInput) =>
    api.put<PromoCode>(`/admin/promo-codes/${id}`, body),

  /** Reversible, and the reason the row keeps its history. */
  disablePromoCode: (id: string) => api.post<{ message: string }>(`/admin/promo-codes/${id}/disable`),

  enablePromoCode: (id: string) => api.post<{ message: string }>(`/admin/promo-codes/${id}/enable`),

  /**
   * Removes the code, who it was for, and who used it. Coins and Gold already granted
   * stay where they are — the ledger has no reference to the code.
   */
  deletePromoCode: (id: string) => api.delete<{ message: string }>(`/admin/promo-codes/${id}`),

  // --- the recipient picker ------------------------------------------------

  /**
   * Accounts a code can be addressed to, one page at a time.
   *
   * The only endpoint in the console that names individual gamers. It exists because a
   * gift has to be addressed to somebody; see the controller for the argument.
   */
  searchUsers: (params: { q?: string; filter?: DirectoryFilter; page?: number }) =>
    api.get<UserDirectory>(
      `/admin/users?${new URLSearchParams({
        ...(params.q ? { q: params.q } : {}),
        filter: params.filter ?? 'ALL',
        page: String(params.page ?? 0),
      }).toString()}`,
    ),

  /** Everybody matching the same search, as ids — what "select all" resolves to. */
  searchUserIds: (params: { q?: string; filter?: DirectoryFilter }) =>
    api.get<UserIds>(
      `/admin/users/ids?${new URLSearchParams({
        ...(params.q ? { q: params.q } : {}),
        filter: params.filter ?? 'ALL',
      }).toString()}`,
    ),
};
