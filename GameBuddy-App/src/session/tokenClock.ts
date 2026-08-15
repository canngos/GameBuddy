/**
 * Reads the timestamps out of a JWT, to decide when to ask for a fresh one.
 *
 * **This does not verify anything, and nothing here is a security decision.** The
 * signature is the server's business; all the app wants is a hint about when its token
 * goes stale, and that hint is already sitting in the payload it was handed. Getting it
 * wrong costs one unnecessary refresh call, or one expiry the server reports anyway.
 *
 * Hand-decoded rather than pulling in a JWT library: it is one base64 payload and two
 * numbers, and a dependency for that would be the larger cost.
 */
export type TokenClock = {
  /** Seconds since the epoch, from `iat`. */
  issuedAt: number;
  /** Seconds since the epoch, from `exp`. */
  expiresAt: number;
};

export function readTokenClock(token: string): TokenClock | null {
  const payload = token.split('.')[1];
  if (!payload) return null;

  try {
    const json = JSON.parse(base64UrlDecode(payload)) as { iat?: number; exp?: number };
    if (typeof json.iat !== 'number' || typeof json.exp !== 'number') return null;
    return { issuedAt: json.iat, expiresAt: json.exp };
  } catch {
    // A token this app cannot read is one it should not try to pace refreshes for.
    // Treated as "no opinion", which leaves the session working until the server
    // refuses it.
    return null;
  }
}

/**
 * Whether the token is far enough through its life to be worth renewing.
 *
 * Half-life, so a refresh has the whole second half of the token's life to succeed in.
 * With the backend's seven days that means the app renews after about three and a half,
 * and a week of failed attempts — no network, backend down — still would not log
 * anybody out early.
 *
 * Unreadable tokens answer false: the server is the authority on expiry, and guessing
 * "probably stale" would refresh on every launch forever.
 */
export function shouldRefresh(token: string, now: number = Date.now()): boolean {
  const clock = readTokenClock(token);
  if (!clock) return false;

  const lifetime = clock.expiresAt - clock.issuedAt;
  if (lifetime <= 0) return false;

  return now / 1000 >= clock.issuedAt + lifetime / 2;
}

/** Base64url → string, without Buffer (React Native has no Node globals). */
function base64UrlDecode(value: string): string {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
  return globalThis.atob(padded);
}
