/**
 * Offline JWT minting.
 *
 * The backend's JwtAuthenticationFilter validates a bearer token by signature, issuer,
 * subject and the principal's `tokensValidFrom` — see
 * common/src/main/java/com/gamebuddy/common/security/JwtService.java. There is no session
 * table lookup, so a token signed with the deployment's JWT_SECRET authenticates any
 * gamer whose email is in the database.
 *
 * That is what makes load testing at 50k DAU possible at all: 20,000 seeded fixtures
 * become 20,000 usable identities in milliseconds, with no registration flow and no login
 * storm distorting the measurement.
 *
 * This is a test harness for a local stack. It needs the real secret, which is why it
 * reads .env rather than taking one as an argument — a secret on a command line ends up in
 * shell history.
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..', '..');

/** Parses the workspace .env into a plain object. Comments and blank lines ignored. */
function env() {
  const text = fs.readFileSync(path.join(ROOT, '.env'), 'utf8');
  const out = {};
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    out[trimmed.slice(0, eq)] = trimmed.slice(eq + 1);
  }
  return out;
}

const b64url = (buf) => buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

/**
 * The signing key, derived exactly as JwtService.buildKey does: try base64 first, fall
 * back to the raw UTF-8 bytes. Getting this wrong produces a token that looks perfectly
 * well-formed and is rejected with a bare 401, which is a miserable thing to debug.
 */
function signingKey(secret) {
  try {
    const decoded = Buffer.from(secret, 'base64');
    // Node's base64 decoder never throws — it ignores invalid characters — so the length
    // check is what actually distinguishes "this was base64" from "this was a passphrase".
    if (decoded.length >= 32 && Buffer.from(decoded).toString('base64').replace(/=+$/, '') === secret.replace(/=+$/, '')) {
      return decoded;
    }
  } catch {
    /* fall through */
  }
  return Buffer.from(secret, 'utf8');
}

/**
 * Mints a token for one email.
 *
 * @param {string} email     the `sub` claim; must match a gamer row
 * @param {object} [opts]
 * @param {number} [opts.ttlSeconds]  default 7 days, matching JWT_EXPIRATION
 * @param {number} [opts.iatOffset]   seconds to add to `iat`; negative back-dates a token,
 *                                    which is how the revocation path gets tested
 */
function mint(email, opts = {}) {
  const { JWT_SECRET } = env();
  if (!JWT_SECRET) throw new Error('JWT_SECRET is not set in .env');

  const now = Math.floor(Date.now() / 1000);
  const iat = now + (opts.iatOffset ?? 0);
  const payload = {
    sub: email,
    iss: opts.issuer ?? 'gamebuddy',
    iat,
    exp: iat + (opts.ttlSeconds ?? 7 * 24 * 60 * 60),
  };

  const header = b64url(Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })));
  const body = b64url(Buffer.from(JSON.stringify(payload)));
  const secret = opts.secret ? Buffer.from(opts.secret, 'utf8') : signingKey(JWT_SECRET);
  const signature = b64url(crypto.createHmac('sha256', secret).update(`${header}.${body}`).digest());

  return `${header}.${body}.${signature}`;
}

module.exports = { mint, env, signingKey };
