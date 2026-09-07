-- Two uniqueness rules the application enforced on its own and the database did not.
--
-- Run after upgrade-2026-13-avatar-review.sql. Idempotent.
--
-- 1. fcm_token. A Firebase device token identifies a handset, not an account, so at most
--    one account may hold it: whoever signed in last. updateFcmToken already detaches it
--    from every other account before claiming it, but that is one method, and the failure
--    it guards against is silent and severe — one person's match and message
--    notifications delivered to another person's phone. Partial, because the column is
--    NULL for almost every row (1220 of 1223 here) and NULLs are only distinct from each
--    other in a plain unique index by accident of the standard; being explicit is
--    clearer, and a partial index over three rows is also far smaller.
--
-- 2. lower(username). The existing gamer_username_key is case-sensitive, so to the
--    database "Sarah" and "sarah" are two different people — which is exactly the pair
--    somebody would register to be mistaken for somebody else. setUsername now compares
--    case-insensitively, but a check-then-insert in application code is a race: two
--    concurrent requests can both find the name free. This closes it.
--
-- Both deduplicate first. Neither found anything to clean in this database, and both
-- clauses are written to be safe rather than because they were needed.

BEGIN;

-- Any token held by more than one account is already broken: the notifications are
-- going somewhere arbitrary. Keep it on the most recently active account and clear the
-- rest — a client re-registers its token on next start, so a cleared row heals itself.
UPDATE gamebuddy.gamer
SET fcm_token = NULL
WHERE user_id IN (
    SELECT user_id
    FROM (
        SELECT user_id,
               ROW_NUMBER() OVER (
                   PARTITION BY fcm_token
                   ORDER BY last_modified_date DESC NULLS LAST, user_id
               ) AS rn
        FROM gamebuddy.gamer
        WHERE fcm_token IS NOT NULL
    ) ranked
    WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_gamer_fcm_token
    ON gamebuddy.gamer USING btree (fcm_token)
    WHERE (fcm_token IS NOT NULL);

-- Usernames that differ only in case. The oldest account keeps the name; the others get
-- part of their user id appended, which is ugly but unique, and leaves the account
-- usable — the alternative is refusing to start. They can pick a new one, and now the
-- one they pick will be checked.
--
-- created_date is NULL on every account registered before upgrade-2026-13, so it cannot
-- be the only ordering: NULLS LAST then user_id makes the choice deterministic either
-- way rather than leaving duplicates behind for the index to trip over.
UPDATE gamebuddy.gamer
SET username = left(username, 12) || '_' || left(replace(user_id, '-', ''), 7)
WHERE user_id IN (
    SELECT user_id
    FROM (
        SELECT user_id,
               ROW_NUMBER() OVER (
                   PARTITION BY lower(username)
                   ORDER BY created_date ASC NULLS LAST, user_id
               ) AS rn
        FROM gamebuddy.gamer
        WHERE username IS NOT NULL
    ) ranked
    WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_gamer_username_lower
    ON gamebuddy.gamer USING btree (lower(username));

COMMIT;
