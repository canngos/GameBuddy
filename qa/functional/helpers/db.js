/**
 * Direct Postgres access, via `docker compose exec psql`.
 *
 * No driver dependency on purpose: the suite runs with `node --test` and nothing else
 * installed, and psql is already in the stack. It is slower per call than a real client,
 * which is fine — this is used for the handful of assertions that cannot be made through
 * the API at all.
 *
 * Two of those matter especially:
 *   - the verification code, because there is no mail server locally, and
 *   - the stored chat message body, because "encrypted at rest" is only true if you look.
 */

const { execFileSync } = require('node:child_process');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const PROJECT = process.env.GB_COMPOSE_PROJECT || 'gamebuddy';

/** Runs SQL and returns rows as arrays of column strings. */
function query(sql) {
  const out = execFileSync(
    'docker',
    ['compose', '-p', PROJECT, 'exec', '-T', 'postgres',
     'psql', '-U', 'gamebuddy', '-d', 'gamebuddy', '-t', '-A', '-F', '', '-c', sql],
    { encoding: 'utf8', cwd: ROOT, maxBuffer: 64 * 1024 * 1024 },
  );
  return out
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => line.split(''));
}

/** First column of the first row, or null. */
function scalar(sql) {
  const rows = query(sql);
  return rows.length ? rows[0][0] : null;
}

const esc = (s) => String(s).replace(/'/g, "''");

/**
 * The verification code for an email, read out of the mail the backend printed.
 *
 * **It used to come from the database, and cannot any more.** `verification_code.code` was
 * a plaintext integer; it is now a bcrypt hash, which is the whole point — a six-digit code
 * sitting in the clear is one database read away from being anybody's account. Nothing can
 * reverse it, including this helper, so the code has to come from the only other place it
 * exists: the email.
 *
 * `docker-compose.yml` sets `MAIL_MODE: log`, so the backend prints every message instead of
 * sending it, one field per line — see `LoggingMailSender`. Blocks are matched on the `to:`
 * line, and every account these tests create has a unique address, so interleaved suites
 * cannot hand each other the wrong code.
 *
 * The last matching block wins: re-requesting a code invalidates its predecessors, so the
 * most recent one is the only usable one.
 */
function verificationCode(email) {
  const out = execFileSync(
    'docker',
    ['compose', '-p', PROJECT, 'logs', '--tail', String(MAIL_LOG_LINES), 'backend'],
    { encoding: 'utf8', cwd: ROOT, maxBuffer: 64 * 1024 * 1024 },
  );

  const wanted = String(email).toLowerCase();
  const lines = out.split(/\r?\n/);
  let found = null;

  for (let i = 0; i < lines.length; i++) {
    if (!lines[i].toLowerCase().includes(`to:      ${wanted}`)) continue;
    // The body follows within a few lines of the recipient; the code is the only run of
    // exactly six digits in it.
    const block = lines.slice(i, i + 12).join('\n');
    const match = block.match(/\b(\d{6})\b/);
    if (match) found = match[1];
  }

  return found;
}

/**
 * How much of the backend log to search.
 *
 * Generous, because a full suite run prints a lot between one account's code and the moment
 * a test asks for it, and cheap, because this is a tail rather than the whole log.
 */
const MAIL_LOG_LINES = 4000;

function gamerId(email) {
  return scalar(`select user_id from gamebuddy.gamer where lower(email) = lower('${esc(email)}');`);
}

/**
 * Every stored message body in a room, as stored.
 *
 * `body` is bytea, so it comes back hex-encoded and is compared against the hex of the
 * plaintext. Doing it this way rather than casting to text means a partially-encrypted or
 * accidentally-plaintext column cannot slip past on an encoding technicality.
 */
function storedMessages(roomId) {
  return query(
    `select encode(body, 'hex') from gamebuddy.chat_message ` +
    `where room_id = '${esc(roomId)}' order by created_at;`,
  ).map((r) => r[0]);
}

/**
 * Removes accounts a test created, by email pattern.
 *
 * Not the one-liner the README documents. That statement —
 *
 *   DELETE FROM gamebuddy.gamer WHERE email LIKE '%@bot.gamebuddy.invalid';
 *
 * — fails, because fifteen of the sixteen foreign keys pointing at `gamer` are NO ACTION
 * rather than CASCADE (only `gamer_platform` cascades). The children have to go first, in
 * dependency order. Filed as a defect: the README gives that statement as the way to
 * remove the fake profiles before real users arrive, and it does not work.
 */
function deleteGamers(emailLike) {
  const ids = `(select user_id from gamebuddy.gamer where email like '${esc(emailLike)}')`;

  // One transaction, in dependency order. Chat before the social graph because rooms are
  // reached through participants; community content before the communities that own it.
  const sql = [
    'begin',
    `delete from gamebuddy.chat_message where room_id in (select room_id from gamebuddy.chat_participant where user_id in ${ids})`,
    `delete from gamebuddy.chat_participant where user_id in ${ids}`,
    'delete from gamebuddy.chat_room where id not in (select room_id from gamebuddy.chat_participant)',

    `delete from gamebuddy.approved_matches where user_id in ${ids} or matched_id in ${ids}`,
    `delete from gamebuddy.friends where user_id in ${ids} or friend_id in ${ids}`,
    `delete from gamebuddy.waiting_friends where user_id in ${ids} or requested_id in ${ids}`,
    `delete from gamebuddy.blocked_friends where gamer_id in ${ids} or blocked_user_id in ${ids}`,
    `delete from gamebuddy.declined_matches where user_id in ${ids} or declined_id in ${ids}`,
    `delete from gamebuddy.unlocked_admirer where user_id in ${ids} or admirer_id in ${ids}`,

    // Lobby content: messages before members before lobbies, and other people's
    // memberships in a doomed lobby have to go with the lobby itself.
    `delete from gamebuddy.lobby_message where sender_id in ${ids} or lobby_id in (select id from gamebuddy.lobby where owner_id in ${ids})`,
    `delete from gamebuddy.lobby_member where user_id in ${ids} or lobby_id in (select id from gamebuddy.lobby where owner_id in ${ids})`,
    `delete from gamebuddy.lobby where owner_id in ${ids}`,

    `delete from gamebuddy.content_report where reporter_id in ${ids} or author_id in ${ids} or reviewed_by in ${ids}`,
    `delete from gamebuddy.coin_ledger where user_id in ${ids}`,
    `delete from gamebuddy.purchase where user_id in ${ids}`,
    `delete from gamebuddy.rewarded_ad_grant where user_id in ${ids}`,
    `delete from gamebuddy.recommendation_impression where user_id in ${ids} or candidate_id in ${ids}`,
    `delete from gamebuddy.funnel_event where user_id in ${ids}`,

    // These two key on gamer_id, not user_id. The inconsistency is the reason the
    // README's one-line delete was never going to work.
    `delete from gamebuddy.gamer_games_join where gamer_id in ${ids}`,
    `delete from gamebuddy.gamer_keywords_join where gamer_id in ${ids}`,
    `delete from gamebuddy.gamer_platform where user_id in ${ids}`,
    `delete from gamebuddy.gamer_badge where user_id in ${ids}`,
    `delete from gamebuddy.gamer_cosmetic where user_id in ${ids}`,

    `delete from gamebuddy.session where email like '${esc(emailLike)}'`,
    `delete from gamebuddy.verification_code where email like '${esc(emailLike)}'`,
    `delete from gamebuddy.gamer where email like '${esc(emailLike)}'`,
    'commit',
  ].join(';\n');

  query(sql);
}

module.exports = { query, scalar, esc, verificationCode, gamerId, storedMessages, deleteGamers };
