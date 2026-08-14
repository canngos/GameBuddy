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
 * The verification code for an email.
 *
 * Read from the database rather than scraped from the log: docker-compose.yml sets
 * MAIL_MODE=smtp despite the comment above it claiming codes are printed, so the log may
 * not contain one at all. The table is authoritative either way.
 */
function verificationCode(email) {
  return scalar(
    `select code from gamebuddy.verification_code ` +
    `where lower(email) = lower('${esc(email)}') order by created_at desc nulls last limit 1;`,
  );
}

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

    `delete from gamebuddy.comment_likes_join where user_id in ${ids}`,
    `delete from gamebuddy.post_likes_join where user_id in ${ids}`,
    `delete from gamebuddy.comment where owner in ${ids}`,
    `delete from gamebuddy.comment where post_id in (select post_id from gamebuddy.post where owner in ${ids})`,
    `delete from gamebuddy.post_likes_join where post_id in (select post_id from gamebuddy.post where owner in ${ids})`,
    `delete from gamebuddy.post where owner in ${ids}`,
    `delete from gamebuddy.community_members_join where user_id in ${ids}`,
    `delete from gamebuddy.community where owner in ${ids}`,

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
