import { File, UploadType } from 'expo-file-system';
import { api, authHeader, notifySessionExpired } from './client';
import { API_BASE_URL } from './config';
import { ApiError, type Envelope } from './envelope';
import type { Avatar, AvatarUpload, Game, Keyword, UserInfo } from './types';

/**
 * The reference data onboarding is built from, plus the profile read.
 *
 * These all require a token — which the app has by then, because verification issues
 * one before onboarding starts.
 */
export const catalogueApi = {
  games: () =>
    api.get<{ games: Game[] }>('/application/get/games').then((d) => d.games),

  popularGames: () =>
    api.get<{ games: Game[] }>('/application/get/popular/games').then((d) => d.games),

  keywords: () =>
    api.get<{ keywords: Keyword[] }>('/application/get/keywords').then((d) => d.keywords),

  /** The stock pictures, for a gamer who does not want to upload their own. */
  avatars: () =>
    api.get<{ avatars: Avatar[] }>('/application/get/avatars').then((d) => d.avatars),

  // Achievements moved to badgesApi, which owns the whole feature — the board, claiming
  // and the showcase.
};

export const profileApi = {
  me: () => api.get<UserInfo>('/application/get/user/info'),
  byId: (userId: string) => api.get<UserInfo>(`/application/get/user/info/${userId}`),

  /**
   * Uploads a photo to use as an avatar.
   *
   * The response is not a plain success. It says which of three things happened, and a
   * caller that shows the picture and says "done" will be lying in two of them:
   *
   * - `APPROVED` — live, everyone can see it;
   * - `PENDING`  — screened as uncertain, or screened while the classifier was down.
   *                Visible to nobody but its owner until a person looks at it;
   * - `REJECTED` — refused.
   *
   * The server re-encodes whatever is sent, which is what strips EXIF — including the
   * GPS coordinates a phone camera writes into a photograph. That happens server-side
   * on purpose: the client cannot be trusted to have done it, because the client is
   * whatever made the request.
   */
  uploadAvatar: async (uri: string, mimeType?: string | null) => {
    // expo-file-system does the upload, rather than fetch with a FormData body.
    //
    // Three attempts at the FormData route failed on device, each for its own reason,
    // and together they say the same thing: React Native's FormData and Blob are not
    // the web ones and cannot carry a file from disk.
    //
    // 1. `{ uri, name, type }` — React Native's own long-standing extension. RN 0.86
    //    ships a spec-compliant FormData that refuses it: "Unsupported FormDataPart
    //    implementation", thrown from inside fetch, so the app reported a connection
    //    problem and blamed the network.
    // 2. `await fetch(uri).then(r => r.blob())` — RN's fetch does not read a `file://`
    //    URI. It resolves with an empty body, so the part arrives zero-length and the
    //    server refuses it as "no image was uploaded". A read that returns nothing
    //    without throwing is the worst shape a bug can have.
    // 3. `new Blob([bytes])` from expo-file-system's `bytes()` — RN's Blob does not
    //    accept a typed array, only strings and other Blobs, so the body was garbage.
    //
    // `File.upload` builds the multipart request natively and never crosses the JS
    // bridge with the bytes at all, which is both correct and considerably faster.
    const response = await new File(uri).upload(`${API_BASE_URL}/application/avatar/upload`, {
      uploadType: UploadType.MULTIPART,
      // Must match @RequestPart("file") on ProfileController.
      fieldName: 'file',
      mimeType: mimeType ?? 'image/jpeg',
      headers: authHeader(),
    });

    // This path bypasses the shared client, so it has to unwrap the envelope itself.
    // Kept deliberately close to `unwrap` in client.ts — same envelope, same ApiError,
    // so a caller cannot tell which transport was used.
    return unwrapUpload<AvatarUpload>(response);
  },
};


/**
 * Unwraps an expo-file-system upload into the same shape everything else returns.
 *
 * `File.upload` hands back a status and a raw string rather than a `Response`, so the
 * shared `unwrap` cannot be reused directly. The behaviour is deliberately identical:
 * same envelope, same `ApiError`, so a screen cannot tell which transport ran.
 */
function unwrapUpload<T>(response: { status: number; body: string }): T {
  let envelope: Envelope<T>;
  try {
    envelope = JSON.parse(response.body) as Envelope<T>;
  } catch {
    if (__DEV__) console.warn('[api] upload returned non-JSON', response.status, response.body.slice(0, 300));
    throw new ApiError('The server returned an unexpected response.', ApiError.UNREADABLE, response.status);
  }

  const status = envelope.status;
  if (response.status < 200 || response.status >= 300 || status?.success === false) {
    if (__DEV__) console.warn('[api] upload failed', response.status, response.body.slice(0, 300));
    const error = new ApiError(
      status?.message?.trim() || 'Something went wrong. Please try again.',
      status?.code ?? String(response.status),
      response.status,
    );
    // Same rule as `unwrap` in client.ts: an expired session must clear the token, or an
    // upload with a dead token surfaces as a generic error and the user stays stuck.
    if (error.isSessionExpired) notifySessionExpired();
    throw error;
  }
  return (envelope.body?.data ?? (undefined as T)) as T;
}
