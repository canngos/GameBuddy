-- Reports become cases: frozen evidence, weighted reporters, a sanction ladder with an
-- audit trail, and a suspension that lifts itself.
--
-- Run after upgrade-2026-44-shelf-reprice.sql. Idempotent. Additive only -- an image built
-- before this migration still boots against the schema it produces.
--
-- What was wrong with reports as they stood, in the order it would have hurt once the app
-- was public:
--
--   * A report recorded nothing but ids. The reported person could change their picture or
--     their name before a moderator looked, and the queue would show the clean version.
--   * A message report was a flag on the message. No reason, no reporter, no context -- a
--     moderator saw one decrypted line and had to guess whether it was a threat or a joke.
--   * Reports were counted, not weighed. Five accounts made an hour ago reporting one person
--     looked exactly like five long-standing users doing the same, and nothing anywhere
--     remembered who had cried wolf before.
--   * Upholding a report did nothing to the account; banning was a separate endpoint the
--     console never called; there was no warning, no time-limited suspension, no record of
--     why, and nothing told either side what happened. The terms promise all of that.
--
-- One case per reported person at a time. Every report against them while it is open joins
-- it; a moderator decides the case once, and every report in it closes with that decision.

BEGIN;

-- ---------------------------------------------------------------------------------------
-- The case: one open row per target.
-- ---------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gamebuddy.moderation_case (
    id                 uuid PRIMARY KEY,
    target_id          character varying(255) NOT NULL,
    status             character varying(16) NOT NULL,
    -- Sum of the reporters' weights, not a count. A weight is the reporter's standing
    -- (upheld vs dismissed history, with a prior for the unknown) halved for an account
    -- under a week old. Recomputed from the reports whenever one joins the case.
    weighted_score     numeric(8, 3) NOT NULL DEFAULT 0,
    distinct_reporters integer NOT NULL DEFAULT 0,
    opened_at          timestamp(6) with time zone NOT NULL,
    last_report_at     timestamp(6) with time zone NOT NULL,
    closed_at          timestamp(6) with time zone,
    closed_by          character varying(255),
    -- The action that closed it (a moderation_action.action value), or null while open.
    outcome            character varying(32),
    -- True while the automatic hide is in force, so a dismissal knows to lift it.
    auto_hidden        boolean NOT NULL DEFAULT false,
    CONSTRAINT moderation_case_status_check
        CHECK (status IN ('OPEN', 'URGENT', 'CLOSED'))
);

-- At most one open case per person: the invariant the whole design rests on.
CREATE UNIQUE INDEX IF NOT EXISTS uq_moderation_case_open_target
    ON gamebuddy.moderation_case (target_id)
    WHERE status <> 'CLOSED';

CREATE INDEX IF NOT EXISTS idx_moderation_case_queue
    ON gamebuddy.moderation_case (status, opened_at);

CREATE INDEX IF NOT EXISTS idx_moderation_case_target
    ON gamebuddy.moderation_case (target_id, opened_at DESC);

COMMENT ON TABLE gamebuddy.moderation_case IS
    'One open case per reported gamer. Reports join it; a moderator resolves it once.';

-- ---------------------------------------------------------------------------------------
-- The report grows a structured reason, a note, a snapshot, and its case.
-- ---------------------------------------------------------------------------------------

ALTER TABLE gamebuddy.content_report
    ADD COLUMN IF NOT EXISTS reason_code character varying(32),
    ADD COLUMN IF NOT EXISTS note        character varying(300),
    -- For MESSAGE reports: the room, so the context can be re-read in order.
    ADD COLUMN IF NOT EXISTS room_id     uuid,
    -- What was there when the report was filed. For a profile: username, avatar key and
    -- status, country, keywords, games. For a message: the reported id and the ids of the
    -- ten messages either side. Ids rather than text for messages -- bodies stay encrypted
    -- and are only decrypted on the audited moderator read path.
    ADD COLUMN IF NOT EXISTS evidence    jsonb,
    ADD COLUMN IF NOT EXISTS case_id     uuid;

ALTER TABLE gamebuddy.content_report
    DROP CONSTRAINT IF EXISTS content_report_content_type_check;

ALTER TABLE gamebuddy.content_report
    ADD CONSTRAINT content_report_content_type_check
    CHECK (content_type IN ('POST', 'COMMENT', 'PROFILE', 'MESSAGE'));

ALTER TABLE gamebuddy.content_report
    DROP CONSTRAINT IF EXISTS content_report_reason_code_check;

ALTER TABLE gamebuddy.content_report
    ADD CONSTRAINT content_report_reason_code_check
    CHECK (reason_code IS NULL OR reason_code IN
        ('HARASSMENT', 'SEXUAL', 'SPAM_SCAM', 'UNDERAGE', 'IMPERSONATION', 'OTHER'));

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_content_report_case') THEN
        ALTER TABLE gamebuddy.content_report
            ADD CONSTRAINT fk_content_report_case
            FOREIGN KEY (case_id) REFERENCES gamebuddy.moderation_case (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_content_report_case
    ON gamebuddy.content_report (case_id);

-- The old "one report per reporter per item, forever" constraint is wrong for cases. A
-- person may report someone, have the case dismissed or upheld and closed, and then need
-- to report the same person again when they reoffend -- a second report against the same
-- profile after the first case closes is legitimate, not a duplicate. Dedup is now scoped
-- to the OPEN case (one report per reporter per case, enforced in the service), so this
-- lifetime constraint would only turn a valid repeat report into a 500.
ALTER TABLE gamebuddy.content_report
    DROP CONSTRAINT IF EXISTS uq_report_once_per_reporter;

-- Backfill: reports left OPEN when this migration runs have no case, and the new queue and
-- the SLA counters read moderation_case, so without this they would vanish from the
-- moderator's view -- breaking the terms' promise to act on every report. Give each
-- distinct still-open target a case and attach its open reports to it.
DO $$
DECLARE
    target RECORD;
    new_case_id uuid;
BEGIN
    FOR target IN
        SELECT DISTINCT author_id
        FROM gamebuddy.content_report
        WHERE status = 'OPEN' AND case_id IS NULL
    LOOP
        new_case_id := gen_random_uuid();
        INSERT INTO gamebuddy.moderation_case
            (id, target_id, status, weighted_score, distinct_reporters, opened_at, last_report_at, auto_hidden)
        SELECT
            new_case_id,
            target.author_id,
            'OPEN',
            0,
            COUNT(DISTINCT reporter_id),
            MIN(created_at),
            MAX(created_at),
            false
        FROM gamebuddy.content_report
        WHERE author_id = target.author_id AND status = 'OPEN' AND case_id IS NULL;

        UPDATE gamebuddy.content_report
        SET case_id = new_case_id
        WHERE author_id = target.author_id AND status = 'OPEN' AND case_id IS NULL;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------------------
-- What a moderator did, and why. Every sanction is a row here, whether or not it came
-- from a case (a ban from the accounts tab has no case).
-- ---------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gamebuddy.moderation_action (
    id           uuid PRIMARY KEY,
    case_id      uuid REFERENCES gamebuddy.moderation_case (id),
    target_id    character varying(255) NOT NULL,
    actor_id     character varying(255) NOT NULL,
    action       character varying(32) NOT NULL,
    reason_code  character varying(32),
    note         character varying(500),
    -- Whether the photo was removed alongside the action; a separate flag because a
    -- warning and a removal are one decision, not two visits.
    photo_removed boolean NOT NULL DEFAULT false,
    -- When a suspension ends. Null for everything that is not a suspension.
    expires_at   timestamp(6) with time zone,
    created_at   timestamp(6) with time zone NOT NULL,
    CONSTRAINT moderation_action_action_check
        CHECK (action IN ('DISMISS', 'WARN', 'REMOVE_PHOTO', 'SUSPEND_24H', 'SUSPEND_7D', 'BAN', 'UNBAN'))
);

CREATE INDEX IF NOT EXISTS idx_moderation_action_target
    ON gamebuddy.moderation_action (target_id, created_at DESC);

COMMENT ON TABLE gamebuddy.moderation_action IS
    'The audit trail. The terms promise the person is told which rule was broken; this is where that is recorded.';

-- ---------------------------------------------------------------------------------------
-- The gamer: reporter standing, suspension, the automatic hide, and a held avatar.
-- ---------------------------------------------------------------------------------------

ALTER TABLE gamebuddy.gamer
    ADD COLUMN IF NOT EXISTS reports_upheld        integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reports_dismissed     integer NOT NULL DEFAULT 0,
    -- Set together with is_blocked = true. Null on a permanent ban; a time on a suspension,
    -- which SuspensionLiftJob clears when it passes.
    ADD COLUMN IF NOT EXISTS suspended_until       timestamp(6) with time zone,
    -- The soft, reversible action: out of every deck, still able to sign in, chat with
    -- existing matches, and be reviewed. Set by the report policy, cleared by the decision.
    ADD COLUMN IF NOT EXISTS hidden_from_discovery boolean NOT NULL DEFAULT false,
    -- When an approved avatar was pulled back into review because of a report. While set,
    -- the auto-publish deadline does not apply: only a person decides this one.
    ADD COLUMN IF NOT EXISTS avatar_held_at        timestamp(6) with time zone;

COMMENT ON COLUMN gamebuddy.gamer.reports_upheld IS
    'Reports this gamer filed that a moderator upheld. With reports_dismissed, the reporter''s weight.';
COMMENT ON COLUMN gamebuddy.gamer.suspended_until IS
    'End of a time-limited block. Null with is_blocked = true means a permanent ban.';
COMMENT ON COLUMN gamebuddy.gamer.hidden_from_discovery IS
    'Automatically hidden from decks by the report policy until a moderator decides the case.';

CREATE INDEX IF NOT EXISTS idx_gamer_suspended_until
    ON gamebuddy.gamer (suspended_until)
    WHERE suspended_until IS NOT NULL;

COMMIT;
