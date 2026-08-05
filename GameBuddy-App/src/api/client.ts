import { API_BASE_URL, REQUEST_TIMEOUT_MS } from './config';
import { ApiError, type Envelope } from './envelope';

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

type RequestOptions = {
  /** Omit the bearer token. Only registration, verification and login need this. */
  anonymous?: boolean;
  signal?: AbortSignal;
  /**
   * Overrides the default. Only the upload path uses it: a photo has to travel over a
   * mobile connection and then be classified server-side, which takes longer than any
   * ordinary request and should not be cut off as if the network had failed.
   */
  timeoutMs?: number;
};

/**
 * Supplies the bearer token. Set once at startup by the session store rather than
 * imported from it, so that `api` does not depend on the store and the store can
 * depend on `api` — the alternative is a require cycle that Metro resolves by handing
 * one of the two modules an empty object.
 */
let tokenProvider: () => string | null = () => null;

export function setTokenProvider(provider: () => string | null): void {
  tokenProvider = provider;
}

/**
 * Called when a request fails because the session is no longer valid. The session
 * store installs a handler that clears the token, which sends the navigation guard
 * back to the sign-in screen.
 */
let onSessionExpired: () => void = () => {};

export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

async function request<T>(
  method: Method,
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };

  // FormData is passed through untouched, and deliberately without a Content-Type:
  // fetch has to set that header itself so it can append the multipart boundary. Setting
  // it here produces a body the server cannot parse and an error that blames the file.
  const isMultipart = typeof FormData !== 'undefined' && body instanceof FormData;
  if (body !== undefined && !isMultipart) headers['Content-Type'] = 'application/json';

  if (!options.anonymous) {
    const token = tokenProvider();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  // Two things can abort: our timeout, and a caller unmounting a screen. Both feed
  // the same controller, so whichever fires first wins.
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? REQUEST_TIMEOUT_MS);
  const unsubscribe = linkSignal(options.signal, controller);

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : isMultipart ? (body as FormData) : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (cause) {
    // A caller-initiated abort is not an error to report; let it propagate so
    // TanStack Query can discard the result instead of rendering a failure.
    if (options.signal?.aborted) throw cause;
    // The friendly message below is the right thing to show a user and the wrong thing
    // to debug against: it turns every transport failure into "check your connection",
    // including ones that have nothing to do with the connection. The real cause is
    // kept in development, where somebody is looking.
    if (__DEV__) console.warn(`[api] ${method} ${path} failed:`, cause);
    throw new ApiError(
      'Could not reach GameBuddy. Check your connection and try again.',
      ApiError.NETWORK,
      0,
    );
  } finally {
    clearTimeout(timeout);
    unsubscribe();
  }

  return unwrap<T>(response, method, path);
}

async function unwrap<T>(response: Response, method: Method, path: string): Promise<T> {
  const text = await response.text();

  // 204, or a failure with nothing in it. The common case of the latter is Spring
  // Security's entry point rejecting an expired token before any controller runs, so
  // this path has to be able to trigger a sign-out — see ApiError.isSessionExpired.
  if (!text) {
    if (response.ok) return undefined as T;
    const error = new ApiError(
      response.status === 401
        ? 'Your session has expired. Please sign in again.'
        : 'The server returned an empty response.',
      ApiError.UNREADABLE,
      response.status,
    );
    if (error.isSessionExpired) onSessionExpired();
    throw error;
  }

  let envelope: Envelope<T>;
  try {
    envelope = JSON.parse(text) as Envelope<T>;
  } catch {
    // Not JSON: almost always an HTML error page from something in front of the
    // backend, or the wrong host entirely.
    console.warn(`[api] ${method} ${path} returned non-JSON (HTTP ${response.status})`);
    throw new ApiError(
      'The server returned an unexpected response.',
      ApiError.UNREADABLE,
      response.status,
    );
  }

  const status = envelope.status;

  // The HTTP status and the envelope agree on every path the backend controls, but
  // trusting `success` alone would let a 500 from anything in between read as OK.
  if (!response.ok || status?.success === false) {
    // The envelope carries a code and a message that the UI may or may not surface —
    // "Something went wrong" is the fallback when it does not. In development the raw
    // answer is worth more than the polished one.
    if (__DEV__) console.warn(`[api] ${method} ${path} -> HTTP ${response.status}`, text.slice(0, 400));
    const error = new ApiError(
      status?.message?.trim() || 'Something went wrong. Please try again.',
      status?.code ?? String(response.status),
      response.status,
    );
    if (error.isSessionExpired) onSessionExpired();
    throw error;
  }

  return (envelope.body?.data ?? (undefined as T)) as T;
}

/** Forwards an external abort into our controller, and stops forwarding afterwards. */
function linkSignal(signal: AbortSignal | undefined, controller: AbortController): () => void {
  if (!signal) return () => {};
  if (signal.aborted) {
    controller.abort();
    return () => {};
  }
  const forward = () => controller.abort();
  signal.addEventListener('abort', forward);
  return () => signal.removeEventListener('abort', forward);
}

/**
 * The Authorization header, for the one caller that cannot go through `request`.
 *
 * expo-file-system builds its own request natively, so it needs the header rather than
 * the token provider. Exported so there is still exactly one place that knows how the
 * header is spelled.
 */
export function authHeader(): Record<string, string> {
  const token = tokenProvider();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>('GET', path, undefined, options),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('POST', path, body, options),
  /**
   * Multipart POST, for the one endpoint that takes a file.
   *
   * Separate from `post` only so the call site reads as an upload; `request` already
   * recognises a FormData body. The longer timeout is the point: an avatar has to travel
   * over a mobile connection and then be classified server-side, which the deck's
   * timeout does not allow for.
   */
  upload: <T>(path: string, form: FormData, options?: RequestOptions) =>
    request<T>('POST', path, form, { ...options, timeoutMs: 60_000 }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('PUT', path, body, options),
  /**
   * DELETE carries a body on `/auth/account`, which requires the password so that a
   * stolen token alone cannot destroy an account.
   */
  delete: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('DELETE', path, body, options),
};
