/**
 * A thin client for the backend's response envelope.
 *
 * Every response is `{ body: {...} | null, status: { code, message, success } }`, and the
 * HTTP status and the envelope's `code` are separate things — a 402 carries code 159,
 * a 429 carries 146 or 158 or 163 depending on which limit was hit. Tests assert on both,
 * because the app branches on the code and a change to either is a breaking change.
 */

const BASE = process.env.GB_BASE_URL || 'http://localhost:8080';

/** Transaction codes, from common/src/main/java/com/gamebuddy/common/enums/TransactionCode.java */
const CODE = {
  SUCCESS: '100',
  EMAIL_EXISTS: '101',
  EMAIL_SEND_FAILED: '102',
  USER_NOT_FOUND: '103',
  VERIFICATION_CODE_NOT_FOUND: '105',
  USER_NOT_VERIFIED: '106',
  USERNAME_EXISTS: '107',
  WRONG_PASSWORD: '108',
  USER_NOT_COMPLETED: '109',
  TOKEN_INVALID: '110',
  USER_BLOCKED: '113',
  RECOMMENDER_SERVICE_ERROR: '123',
  COIN_NOT_ENOUGH: '129',
  NOT_MEMBER: '132',
  NOT_OWNER: '134',
  ALREADY_LIKED: '139',
  NOT_ADMIN: '140',
  TOO_MANY_ATTEMPTS: '145',
  RATE_LIMITED: '146',
  WEAK_PASSWORD: '147',
  INVALID_REQUEST: '148',
  FORBIDDEN: '149',
  NOT_MATCHED: '153',
  AGE_BAND_MISMATCH: '154',
  ALREADY_REPORTED: '157',
  ACCEPT_LIMIT_REACHED: '158',
  SUBSCRIPTION_REQUIRED: '159',
  SWIPE_LIMIT_REACHED: '163',
  CONTENT_BLOCKED: '170',
  LOBBY_NOT_FOUND: '178',
  LOBBY_FULL: '179',
  LOBBY_NOT_OPEN: '180',
  LOBBY_ALREADY_MEMBER: '181',
  LOBBY_NOT_MEMBER: '182',
  LOBBY_LIMIT_REACHED: '183',
  LOBBY_REQUEST_NOT_FOUND: '184',
  LOBBY_REJECTED: '185',
};

/** Controller base paths, so a test never hard-codes a prefix that moved. */
const P = {
  auth: '/auth',
  profile: '/application',
  match: '/match',
  chat: '', // ChatController has no @RequestMapping — /messages/**, /presence/**
  community: '/community',
  lobby: '/lobby',
  notif: '/notif',
  billing: '/billing',
  coins: '/coins',
  ads: '/ads',
  admin: '/admin',
  badges: '/application/badges',
  cosmetics: '/application/cosmetics',
  analytics: '/analytics',
};

async function request(method, path, { token, body, headers = {}, raw = false } = {}) {
  const init = { method, headers: { ...headers } };
  if (token) init.headers.Authorization = `Bearer ${token}`;
  if (body !== undefined && !raw) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  } else if (raw) {
    init.body = body;
  }

  const res = await fetch(`${BASE}${path}`, init);
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* non-JSON responses (media, actuator) are handed back as text */
  }

  return {
    status: res.status,
    headers: res.headers,
    json,
    text,
    /** The envelope's transaction code, or null for a non-enveloped response. */
    code: json?.status?.code ?? null,
    message: json?.status?.message ?? null,
    success: json?.status?.success ?? null,
    data: json?.body?.data ?? json?.body ?? null,
  };
}

const get = (path, opts) => request('GET', path, opts);
const post = (path, body, opts) => request('POST', path, { ...opts, body });
const put = (path, body, opts) => request('PUT', path, { ...opts, body });
const del = (path, opts) => request('DELETE', path, opts);

module.exports = { BASE, CODE, P, request, get, post, put, del };
