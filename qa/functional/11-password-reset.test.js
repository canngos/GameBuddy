/**
 * Forgetting a password and getting back in.
 *
 * The flow is three calls: ask for a code (`/auth/sendCode` with `isRegister:false`), spend
 * it for a short-lived ticket (`/auth/reset/verify`), spend the ticket to set the password
 * (`/auth/reset/pwd`). Everything worth asserting here is about what the flow refuses.
 *
 * The two properties that matter most, and that nothing else in the suite covers:
 *
 * - **Codes are scoped.** A signup code cannot reset a password and a reset code cannot sign
 *   anyone in. Before that was true, a code mailed as "Password Reset" was redeemable at
 *   `/auth/verify`, which hands back a session — a passwordless login by another name.
 * - **A reset ends every session.** The point of resetting is usually that somebody else may
 *   be holding a token.
 */

const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { get, post, P, CODE } = require('./helpers/api');
const { createAccount, cleanup, PASSWORD } = require('./helpers/accounts');
const db = require('./helpers/db');

const VERIFICATION_CODE_INVALID = '105';
const PASSWORD_SAME = '112';
const WEAK_PASSWORD = '147';

const NEW_PASSWORD = 'R3setPassw0rd';

/** Asks for a reset code and reads it out of the mail the backend printed. */
async function requestResetCode(email) {
  const sent = await post(`${P.auth}/sendCode`, { email, isRegister: false });
  assert.equal(sent.status, 200, sent.text);
  const code = db.verificationCode(email);
  assert.ok(code, `no reset code was mailed to ${email}`);
  return Number(code);
}

describe('password reset', () => {
  after(() => cleanup());

  describe('the happy path', () => {
    let account;
    let token;

    before(async () => {
      account = await createAccount();
      // A session from before the reset, to prove it does not survive one.
      const login = await post(`${P.auth}/login`, {
        usernameOrEmail: account.email,
        password: PASSWORD,
      });
      token = login.data?.accessToken;
      assert.ok(token, 'expected a session to start from');

      // Revocation compares at second granularity on purpose (JwtService.isRevoked), so a
      // token minted in the same second as the reset deliberately survives. Without this
      // pause the "old session is dead" assertion below races that grace period.
      await new Promise((r) => setTimeout(r, 1200));
    });

    test('a mailed code is exchanged for a reset ticket', async () => {
      const code = await requestResetCode(account.email);

      const verified = await post(`${P.auth}/reset/verify`, {
        email: account.email,
        verificationCode: code,
      });

      assert.equal(verified.status, 200, verified.text);
      account.resetToken = verified.data?.resetToken;
      assert.ok(account.resetToken, 'expected a reset token');
      assert.notEqual(account.resetToken, String(code), 'the ticket must not be the code');
    });

    test('the ticket sets a new password', async () => {
      const reset = await post(`${P.auth}/reset/pwd`, {
        email: account.email,
        resetToken: account.resetToken,
        password: NEW_PASSWORD,
      });
      assert.equal(reset.status, 200, reset.text);
    });

    test('the new password works and the old one does not', async () => {
      const withNew = await post(`${P.auth}/login`, {
        usernameOrEmail: account.email,
        password: NEW_PASSWORD,
      });
      assert.equal(withNew.status, 200, withNew.text);

      const withOld = await post(`${P.auth}/login`, {
        usernameOrEmail: account.email,
        password: PASSWORD,
      });
      assert.equal(withOld.code, CODE.WRONG_PASSWORD, 'the old password must stop working');
    });

    test('the session held before the reset is dead', async () => {
      const me = await get(`${P.profile}/get/user/info`, { token });
      assert.equal(me.status, 401, 'a token issued before the reset must be refused');
    });

    test('the ticket cannot be spent twice', async () => {
      const again = await post(`${P.auth}/reset/pwd`, {
        email: account.email,
        resetToken: account.resetToken,
        password: 'An0therPassw0rd',
      });
      assert.equal(again.code, VERIFICATION_CODE_INVALID, again.text);
    });
  });

  describe('what it refuses', () => {
    test('an address with no account is answered exactly like one that has', async () => {
      // Unique per run: sendCode is throttled per address for fifteen minutes, so a fixed
      // one would pass on the first run of the day and 429 on the fourth.
      const nobody = await post(`${P.auth}/sendCode`, {
        email: `no-such-person-${Date.now()}@qa.gamebuddy.invalid`,
        isRegister: false,
      });
      assert.equal(nobody.status, 200, 'a missing account must not be distinguishable');
      assert.equal(nobody.code, CODE.SUCCESS);
    });

    test('a wrong code is refused', async () => {
      const account = await createAccount();
      const code = await requestResetCode(account.email);
      const wrong = code === 999999 ? 111111 : 999999;

      const attempt = await post(`${P.auth}/reset/verify`, {
        email: account.email,
        verificationCode: wrong,
      });
      assert.equal(attempt.code, VERIFICATION_CODE_INVALID, attempt.text);
    });

    /** The scoping rule, in the direction that used to be a passwordless login. */
    test('a reset code cannot be redeemed at /auth/verify', async () => {
      const account = await createAccount();
      const code = await requestResetCode(account.email);

      const misused = await post(`${P.auth}/verify`, {
        email: account.email,
        verificationCode: code,
      });

      assert.equal(misused.code, VERIFICATION_CODE_INVALID, misused.text);
      assert.equal(misused.data?.accessToken, undefined, 'no session may come out of this');
    });

    /** And the other direction: a signup code is not a reset. */
    test('a signup code cannot be redeemed at /auth/reset/verify', async () => {
      const email = `qa-reset-${Date.now()}@qa.gamebuddy.invalid`;
      const registered = await post(`${P.auth}/register`, {
        email,
        password: PASSWORD,
        acceptedTerms: true,
      });
      // 201, not 200: register creates and deliberately returns no token.
      assert.equal(registered.status, 201, registered.text);

      const code = Number(db.verificationCode(email));
      assert.ok(code, 'expected a signup code');

      const misused = await post(`${P.auth}/reset/verify`, { email, verificationCode: code });
      assert.equal(misused.code, VERIFICATION_CODE_INVALID, misused.text);
    });

    test('an unknown ticket is refused', async () => {
      const account = await createAccount();
      const attempt = await post(`${P.auth}/reset/pwd`, {
        email: account.email,
        resetToken: 'f'.repeat(64),
        password: NEW_PASSWORD,
      });
      assert.equal(attempt.code, VERIFICATION_CODE_INVALID, attempt.text);
    });

    test('a weak password is refused, and the old one still works', async () => {
      const account = await createAccount();
      const code = await requestResetCode(account.email);
      const verified = await post(`${P.auth}/reset/verify`, {
        email: account.email,
        verificationCode: code,
      });

      const weak = await post(`${P.auth}/reset/pwd`, {
        email: account.email,
        resetToken: verified.data.resetToken,
        password: 'password',
      });
      assert.equal(weak.code, WEAK_PASSWORD, weak.text);

      const stillIn = await post(`${P.auth}/login`, {
        usernameOrEmail: account.email,
        password: PASSWORD,
      });
      assert.equal(stillIn.status, 200, 'a refused reset must leave the account alone');
    });

    test('reusing the current password is refused', async () => {
      const account = await createAccount();
      const code = await requestResetCode(account.email);
      const verified = await post(`${P.auth}/reset/verify`, {
        email: account.email,
        verificationCode: code,
      });

      const same = await post(`${P.auth}/reset/pwd`, {
        email: account.email,
        resetToken: verified.data.resetToken,
        password: PASSWORD,
      });
      assert.equal(same.code, PASSWORD_SAME, same.text);
    });
  });

  describe('what it stores', () => {
    test('the code is never in the database in the clear', async () => {
      const account = await createAccount();
      const code = await requestResetCode(account.email);

      const rows = db.query(
        `select code_hash, purpose from gamebuddy.verification_code ` +
          `where lower(email) = lower('${db.esc(account.email)}') order by created_at desc limit 1;`,
      );
      assert.equal(rows.length, 1, 'expected the issued code row');
      const [hash, purpose] = rows[0];

      assert.equal(purpose, 'PASSWORD_RESET');
      assert.ok(hash.startsWith('$2'), `expected a bcrypt hash, got ${hash}`);
      assert.ok(!hash.includes(String(code)), 'the digits must not appear in the stored value');
    });
  });
});
