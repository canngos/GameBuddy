-- Lobbies: an open invitation to play one game, together, at a stated time.
--
-- Apply after upgrade-2026-26-column-bounds.sql. Idempotent: safe to run twice.
--
-- The Community tab never connected to the thing this app is for — finding people to play
-- with. A lobby is that thing in group form: a Gold member opens one ("Valorant, chill,
-- 5 of us, tonight at nine, mic please"), strangers ask to join, the owner picks the team,
-- and everyone accepted talks details in the lobby's own chat. Communities are retired in
-- a later migration once lobbies have shipped; nothing here touches the community tables.

SET search_path TO gamebuddy;

BEGIN;

-- ---------------------------------------------------------------------------
-- lobby — one row per opened lobby, through its whole life
-- ---------------------------------------------------------------------------
--
-- Status is the owner's word, not a clock's. OPEN means requests are welcome. LOCKED means
-- the team is found and the owner wants quiet — it starts no timer, because a team found
-- at four may not play until seven. ENDED and CANCELLED are terminal; ARCHIVED is the
-- sweeper filing them away (and deleting the chat) after a month. FILLING deliberately
-- does not exist: it is just OPEN with members, and a stored copy of a derivable fact is
-- one more thing that can be wrong.
--
-- starts_at is the *planned* time, entered by the owner. The sweeper reads it generously
-- (a day late before presuming abandonment), never punctually.

CREATE TABLE IF NOT EXISTS lobby (
    id            uuid PRIMARY KEY,
    owner_id      varchar(255) NOT NULL REFERENCES gamer (user_id),
    game_id       varchar(255) NOT NULL REFERENCES games (game_id),
    title         varchar(80)  NOT NULL,
    description   varchar(500),
    requirements  varchar(300),
    tone          varchar(16)  NOT NULL,
    max_players   integer      NOT NULL,
    starts_at     timestamptz  NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'OPEN',
    locked_at     timestamptz,
    ended_at      timestamptz,
    version       bigint       NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT lobby_max_players_check CHECK (max_players BETWEEN 2 AND 5),
    CONSTRAINT lobby_tone_check
        CHECK (tone IN ('COMPETITIVE', 'CHILL', 'CASUAL', 'LEARNING')),
    CONSTRAINT lobby_status_check
        CHECK (status IN ('OPEN', 'LOCKED', 'ENDED', 'CANCELLED', 'ARCHIVED'))
);

COMMENT ON TABLE lobby IS
    'An open game lobby: one game, one owner, up to five players, a planned time.';
COMMENT ON COLUMN lobby.requirements IS
    'Owner''s free-text entry bar (mic, rank, in-game chat). Informational only — the '
    'owner screens every join request by hand, so nothing enforces this.';
COMMENT ON COLUMN lobby.status IS
    'OPEN takes requests; LOCKED is "team found, stop asking" and starts NO timer; '
    'ENDED/CANCELLED are terminal; ARCHIVED is swept out of the app after 30 days.';
COMMENT ON COLUMN lobby.starts_at IS
    'The planned start, per the owner. The lifecycle sweeper cancels a still-OPEN lobby '
    '24h past this, and ends a LOCKED one 48h past it. Locking itself schedules nothing.';

-- One live lobby per owner, guaranteed here rather than by everyone remembering. The
-- service refuses first with a friendly error; this index is the backstop for the race.
-- Partial unique indexes cannot be said in JPA, so this exists only in SQL — do not look
-- for it on the entity.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lobby_active_owner
    ON lobby (owner_id) WHERE status IN ('OPEN', 'LOCKED');

-- The browse feed: OPEN lobbies, soonest first.
CREATE INDEX IF NOT EXISTS idx_lobby_browse ON lobby (status, starts_at);

-- The browse feed filtered to one game, which is how most people will look.
CREATE INDEX IF NOT EXISTS idx_lobby_game ON lobby (game_id) WHERE status = 'OPEN';

-- ---------------------------------------------------------------------------
-- lobby_member — requests and memberships are the same row, in different states
-- ---------------------------------------------------------------------------
--
-- A join request that is accepted *becomes* the membership, so they share a table, and
-- the primary key makes asking twice impossible by construction — the same trick
-- content_report plays with uq_report_once_per_reporter. REJECTED is final for this
-- lobby: the owner said no once and does not need to keep saying it. LEFT may come back
-- (the row flips to PENDING again). The owner holds an OWNER row so that "everyone in
-- the chat" is exactly "every row in (OWNER, ACCEPTED)" — but lobby.owner_id, not this
-- table, is what owner-only actions check.

CREATE TABLE IF NOT EXISTS lobby_member (
    lobby_id     uuid         NOT NULL REFERENCES lobby (id),
    user_id      varchar(255) NOT NULL REFERENCES gamer (user_id),
    status       varchar(16)  NOT NULL,
    requested_at timestamptz  NOT NULL DEFAULT now(),
    decided_at   timestamptz,
    last_read_at timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (lobby_id, user_id),
    CONSTRAINT lobby_member_status_check
        CHECK (status IN ('OWNER', 'PENDING', 'ACCEPTED', 'REJECTED', 'LEFT', 'KICKED'))
);

COMMENT ON TABLE lobby_member IS
    'Join requests and memberships in one: PENDING is a request, ACCEPTED/OWNER are the '
    'team, REJECTED is a final no, LEFT and KICKED are how people go.';
COMMENT ON COLUMN lobby_member.last_read_at IS
    'Chat read watermark, same shape as chat_participant.last_read_at. Unread count is '
    'messages newer than this.';

-- "My lobbies" and "my pending requests", asked on every visit to the tab.
CREATE INDEX IF NOT EXISTS idx_lobby_member_user ON lobby_member (user_id, status);

-- ---------------------------------------------------------------------------
-- lobby_message — the lobby's chat
-- ---------------------------------------------------------------------------
--
-- Same at-rest encryption as chat_message (AES-GCM body + nonce + key version, readable
-- by moderators, not end-to-end), but its own table: chat_room is structurally two-party
-- (a unique sorted pair key) and demands the pair be matched, neither of which is true
-- here. The lobby itself is the room, so no room table exists at all.
--
-- Rows are deleted when the sweeper archives the lobby: the conversation had a shelf
-- life by design, and a plan for last Tuesday's game is not worth storing forever.

CREATE TABLE IF NOT EXISTS lobby_message (
    id          uuid PRIMARY KEY,
    lobby_id    uuid         NOT NULL REFERENCES lobby (id),
    sender_id   varchar(255) NOT NULL,
    body        bytea        NOT NULL,
    nonce       bytea        NOT NULL,
    key_version smallint     NOT NULL,
    created_at  timestamptz  NOT NULL
);

COMMENT ON TABLE lobby_message IS
    'Lobby chat, encrypted at rest like chat_message. Deleted when the lobby archives.';

-- The only read: one lobby's history, oldest first.
CREATE INDEX IF NOT EXISTS idx_lobby_message_lobby ON lobby_message (lobby_id, created_at);

COMMIT;
