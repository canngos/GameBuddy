--
-- GameBuddy baseline schema.
--
-- Creates an empty database from nothing. Apply this first, then any upgrade-*.sql newer
-- than it, in numeric order (4, 5, ... 20 — NOT filename order, which puts 10 before 4).
--
-- Generated from the JPA entities and then committed, rather than left to Hibernate at
-- startup. The point is that both environments can run ddl-auto=validate: the schema is
-- built by SQL that can be reviewed and rolled forward, and the application's job is to
-- refuse to start if what it finds does not match what it expects. With ddl-auto=update
-- the application silently reshapes the database instead, which is convenient exactly
-- until it quietly diverges from production.
--
-- HOW TO REGENERATE, AND THE MISTAKE NOT TO REPEAT
--
-- Dump a database built by replaying THIS FILE PLUS EVERY UPGRADE — never one built by
-- pointing Hibernate at an empty database with ddl-auto=create.
--
-- Both produce the same tables and columns, so the difference is invisible in a diff of
-- table names, and it is not invisible at runtime: an entity-generated schema carries no
-- column DEFAULTs at all. Hibernate does not emit them, because it supplies those values
-- from Java. Every DEFAULT in this file — 55 of them — came from a migration, and the
-- seed scripts and the synthetic population loader both rely on them. Regenerating from
-- the entities drops all 48, and the first symptom is `seed-local.sql` failing on a NOT
-- NULL column three steps later.
--
-- The procedure is in the README under "Regenerating the baseline schema". Prove the
-- result by loading it into an empty database and booting with ddl-auto=validate.
--
SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: gamebuddy; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA gamebuddy;


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: gamebuddy; Owner: -
--

CREATE FUNCTION gamebuddy.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Only when something actually changed. An UPDATE that writes the same values is not
    -- a modification, and treating it as one makes "last changed" mean "last touched by
    -- any job that happened to rewrite the row".
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at = now();
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: approved_matches; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.approved_matches (
    matched_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: avatars; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.avatars (
    id uuid NOT NULL,
    image character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: blocked_friends; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.blocked_friends (
    blocked_user_id character varying(255) NOT NULL,
    gamer_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_message; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.chat_message (
    key_version smallint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    reported_at timestamp(6) with time zone,
    id uuid NOT NULL,
    room_id uuid NOT NULL,
    sender_id character varying(255) NOT NULL,
    body bytea NOT NULL,
    nonce bytea NOT NULL
);


--
-- Name: chat_participant; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.chat_participant (
    last_read_at timestamp(6) with time zone,
    room_id uuid NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_room; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.chat_room (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    pair_key character varying(512) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: coin_ledger; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.coin_ledger (
    id uuid NOT NULL,
    user_id character varying(255) NOT NULL,
    delta integer NOT NULL,
    reason character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE coin_ledger; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON TABLE gamebuddy.coin_ledger IS 'Every coin movement, signed. Positive earns, negative spends.';


--
-- Name: comment; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.comment (
    created_date timestamp(6) with time zone NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    comment_id uuid NOT NULL,
    post_id uuid NOT NULL,
    message character varying(255),
    owner character varying(255),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: comment_likes_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.comment_likes_join (
    comment_id uuid NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: community; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.community (
    created_date timestamp(6) with time zone NOT NULL,
    community_id uuid NOT NULL,
    description character varying(2000),
    community_avatar character varying(255),
    name character varying(255) NOT NULL,
    owner character varying(255) NOT NULL,
    wallpaper character varying(255),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: community_members_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.community_members_join (
    community_id uuid NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: content_report; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.content_report (
    created_at timestamp(6) with time zone NOT NULL,
    reviewed_at timestamp(6) with time zone,
    content_id uuid NOT NULL,
    id uuid NOT NULL,
    reason character varying(500),
    author_id character varying(255) NOT NULL,
    content_type character varying(255) NOT NULL,
    reporter_id character varying(255) NOT NULL,
    reviewed_by character varying(255),
    status character varying(255) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT content_report_content_type_check CHECK (((content_type)::text = ANY ((ARRAY['POST'::character varying, 'COMMENT'::character varying, 'PROFILE'::character varying])::text[]))),
    CONSTRAINT content_report_status_check CHECK (((status)::text = ANY (ARRAY[('OPEN'::character varying)::text, ('ACTIONED'::character varying)::text, ('DISMISSED'::character varying)::text])))
);


--
-- Name: cosmetic; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.cosmetic (
    id uuid NOT NULL,
    kind character varying(16) NOT NULL,
    name character varying(80) NOT NULL,
    asset_key character varying(255) NOT NULL,
    animated boolean DEFAULT false NOT NULL,
    price integer DEFAULT 0 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_date timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    membership_only boolean DEFAULT false NOT NULL,
    CONSTRAINT cosmetic_kind_check CHECK (((kind)::text = ANY (ARRAY[('FRAME'::character varying)::text, ('BANNER'::character varying)::text]))),
    CONSTRAINT cosmetic_price_check CHECK ((price >= 0))
);


--
-- Name: declined_matches; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.declined_matches (
    declined_at timestamp(6) with time zone NOT NULL,
    declined_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: friends; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.friends (
    friend_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: funnel_event; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.funnel_event (
    id uuid NOT NULL,
    user_id character varying(255) NOT NULL,
    kind character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE funnel_event; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON TABLE gamebuddy.funnel_event IS 'Client-reported funnel steps. Nothing here grants anything; see CoinLedger for money.';


--
-- Name: gamer; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer (
    accepts_used integer NOT NULL,
    age integer,
    coin integer NOT NULL,
    is_blocked boolean NOT NULL,
    is_registered boolean NOT NULL,
    is_verified boolean NOT NULL,
    swipes_used integer NOT NULL,
    created_date timestamp(6) with time zone,
    deleted_at timestamp(6) with time zone,
    last_modified_date timestamp(6) with time zone NOT NULL,
    quota_reset_at timestamp(6) with time zone,
    subscription_expires_at timestamp(6) with time zone,
    tokens_valid_from timestamp(6) with time zone,
    version bigint NOT NULL,
    avatar uuid,
    subscription_tier character varying(16) NOT NULL,
    country character varying(255),
    email character varying(255) NOT NULL,
    fcm_token character varying(255),
    gender character varying(255),
    pwd character varying(255),
    role character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    username character varying(255),
    avatar_key character varying(255),
    avatar_status character varying(16),
    equipped_frame_id uuid,
    equipped_banner_id uuid,
    last_active_at timestamp with time zone,
    last_nudged_at timestamp with time zone,
    nudge_count integer DEFAULT 0 NOT NULL,
    reminders_enabled boolean DEFAULT true NOT NULL,
    notify_messages boolean DEFAULT true NOT NULL,
    notify_social boolean DEFAULT true NOT NULL,
    notify_communities boolean DEFAULT true NOT NULL,
    recommender_profile_changed_at timestamp(6) with time zone,
    avatar_score double precision,
    avatar_uploaded_at timestamp(6) with time zone,
    birth_date date,
    terms_accepted_at timestamp(6) with time zone,
    terms_version character varying(32),
    last_decision_user_id character varying(255),
    last_decision_accept boolean,
    last_decision_at timestamp with time zone,
    boost_expires_at timestamp with time zone,
    last_free_boost_at timestamp with time zone,
    daily_claimed_at timestamp with time zone,
    daily_streak integer DEFAULT 0 NOT NULL,
    stipend_claimed_at timestamp with time zone,
    quest_week_started_at timestamp with time zone,
    quest_base_messages integer DEFAULT 0 NOT NULL,
    quest_base_matches integer DEFAULT 0 NOT NULL,
    quest_base_posts integer DEFAULT 0 NOT NULL,
    quest_claimed_mask integer DEFAULT 0 NOT NULL,
    super_likes integer DEFAULT 0 NOT NULL,
    bonus_accepts integer DEFAULT 0 NOT NULL,
    like_cap_cohort character varying(16),
    upgrade_prompt_shown_at timestamp with time zone,
    rewarded_ads_today integer DEFAULT 0 NOT NULL,
    rewarded_ad_day timestamp with time zone,
    CONSTRAINT gamer_avatar_status_check CHECK (((avatar_status IS NULL) OR ((avatar_status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text])))),
    CONSTRAINT gamer_role_check CHECK (((role)::text = ANY (ARRAY[('USER'::character varying)::text, ('ADMIN'::character varying)::text]))),
    CONSTRAINT gamer_subscription_tier_check CHECK (((subscription_tier)::text = ANY (ARRAY[('BASIC'::character varying)::text, ('GOLD'::character varying)::text])))
);


--
-- Name: COLUMN gamer.last_decision_user_id; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.last_decision_user_id IS 'Who the most recent swipe was about. NULL when there is nothing to rewind.';


--
-- Name: COLUMN gamer.last_decision_accept; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.last_decision_accept IS 'True if that swipe was a like. Decides which table a rewind has to undo.';


--
-- Name: COLUMN gamer.boost_expires_at; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.boost_expires_at IS 'While in the future, this gamer is pinned to the front of decks in their country.';


--
-- Name: COLUMN gamer.last_free_boost_at; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.last_free_boost_at IS 'When the weekly Gold boost was last taken. NULL means never.';


--
-- Name: COLUMN gamer.daily_claimed_at; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.daily_claimed_at IS 'When the daily coins were last taken. Drives both "again yet?" and the streak.';


--
-- Name: COLUMN gamer.daily_streak; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.daily_streak IS 'Consecutive days claimed. Resets to 1 after a missed day, never to 0 by a claim.';


--
-- Name: COLUMN gamer.quest_week_started_at; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.quest_week_started_at IS 'Start of the week the baselines below were taken at. Null means never started one.';


--
-- Name: COLUMN gamer.quest_claimed_mask; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.quest_claimed_mask IS 'Which of this week''s quests have been paid. Cleared when the week rolls over.';


--
-- Name: COLUMN gamer.super_likes; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.super_likes IS 'Owned super likes, spent one per highlighted like. Never expires.';


--
-- Name: COLUMN gamer.bonus_accepts; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.bonus_accepts IS 'Extra likes added to TODAY''s cap. Cleared when the daily quota window rolls.';


--
-- Name: COLUMN gamer.like_cap_cohort; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.like_cap_cohort IS 'Stable A/B bucket for the free daily like cap. Assigned once, at registration.';


--
-- Name: COLUMN gamer.upgrade_prompt_shown_at; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.upgrade_prompt_shown_at IS 'When the one-time day-3 Gold prompt was shown. Null means never; set once, never cleared.';


--
-- Name: COLUMN gamer.rewarded_ads_today; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.rewarded_ads_today IS 'Rewarded adverts paid for during rewarded_ad_day. Meaningless once that day has passed.';


--
-- Name: COLUMN gamer.rewarded_ad_day; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.gamer.rewarded_ad_day IS 'UTC midnight of the day rewarded_ads_today counts. Null means never watched one.';


--
-- Name: gamer_badge; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_badge (
    user_id character varying(255) NOT NULL,
    badge_code character varying(48) NOT NULL,
    earned_at timestamp with time zone DEFAULT now() NOT NULL,
    collected_at timestamp with time zone,
    showcase_slot integer,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT gamer_badge_slot_check CHECK (((showcase_slot >= 0) AND (showcase_slot <= 2)))
);


--
-- Name: gamer_cosmetic; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_cosmetic (
    user_id character varying(255) NOT NULL,
    cosmetic_id uuid NOT NULL,
    paid integer DEFAULT 0 NOT NULL,
    acquired_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: gamer_games_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_games_join (
    game_id character varying(255) NOT NULL,
    gamer_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: gamer_keywords_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_keywords_join (
    keyword_id uuid NOT NULL,
    gamer_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: gamer_platform; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_platform (
    user_id character varying(255) NOT NULL,
    platform character varying(16) NOT NULL
);


--
-- Name: TABLE gamer_platform; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON TABLE gamebuddy.gamer_platform IS 'Which platforms each gamer plays on. A set, not a single value.';


--
-- Name: games; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.games (
    avg_vote real,
    is_popular boolean,
    category character varying(255),
    description character varying(255),
    game_icon character varying(255),
    game_id character varying(255) NOT NULL,
    game_name character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: keywords; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.keywords (
    created_date timestamp(6) with time zone,
    id uuid NOT NULL,
    description character varying(255),
    keyword_name character varying(255),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notification_outbox; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.notification_outbox (
    attempts integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    next_attempt_at timestamp(6) with time zone NOT NULL,
    sent_at timestamp(6) with time zone,
    id uuid NOT NULL,
    title character varying(200) NOT NULL,
    last_error character varying(500),
    fcm_token character varying(512) NOT NULL,
    body character varying(1000) NOT NULL,
    kind character varying(32) DEFAULT 'GENERAL'::character varying NOT NULL,
    target_id character varying(64),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.notifications (
    is_topic boolean NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    body character varying(1000),
    recipient character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: post; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.post (
    created_date timestamp(6) with time zone NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    community_id uuid NOT NULL,
    post_id uuid NOT NULL,
    body character varying(4000),
    owner character varying(255),
    picture character varying(255),
    title character varying(255),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: post_likes_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.post_likes_join (
    post_id uuid NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: purchase; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.purchase (
    entitlement_expires_at timestamp(6) with time zone,
    purchased_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    platform character varying(16) NOT NULL,
    status character varying(16) NOT NULL,
    receipt character varying(4000),
    product_id character varying(255) NOT NULL,
    store_transaction_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    period_type character varying(16),
    event_type character varying(32),
    CONSTRAINT purchase_platform_check CHECK (((platform)::text = ANY (ARRAY[('APPLE_APP_STORE'::character varying)::text, ('GOOGLE_PLAY'::character varying)::text]))),
    CONSTRAINT purchase_status_check CHECK (((status)::text = ANY (ARRAY[('GRANTED'::character varying)::text, ('REFUNDED'::character varying)::text])))
);


--
-- Name: COLUMN purchase.period_type; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.purchase.period_type IS 'RevenueCat period_type. TRIAL is what separates a trial start from a paid month.';


--
-- Name: COLUMN purchase.event_type; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON COLUMN gamebuddy.purchase.event_type IS 'RevenueCat event type. RENEWAL is what makes month-2 retention countable.';


--
-- Name: recommendation_impression; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.recommendation_impression (
    "position" integer NOT NULL,
    served_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    source character varying(16) NOT NULL,
    candidate_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT recommendation_impression_source_check CHECK (((source)::text = ANY (ARRAY[('MODEL'::character varying)::text, ('EXPLORATION'::character varying)::text])))
);


--
-- Name: rewarded_ad_grant; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.rewarded_ad_grant (
    transaction_id character varying(128) NOT NULL,
    user_id character varying(255) NOT NULL,
    coins integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE rewarded_ad_grant; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON TABLE gamebuddy.rewarded_ad_grant IS 'Rewarded-ad callbacks already honoured. The primary key is the replay defence.';


--
-- Name: session; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.session (
    created_date timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    email character varying(255) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: unlocked_admirer; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.unlocked_admirer (
    user_id character varying(255) NOT NULL,
    admirer_id character varying(255) NOT NULL,
    unlocked_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE unlocked_admirer; Type: COMMENT; Schema: gamebuddy; Owner: -
--

COMMENT ON TABLE gamebuddy.unlocked_admirer IS 'Admirers revealed one at a time with coins, by gamers without Gold.';


--
-- Name: verification_code; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.verification_code (
    attempts integer NOT NULL,
    code integer NOT NULL,
    is_valid boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: waiting_friends; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.waiting_friends (
    requested_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: approved_matches approved_matches_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.approved_matches
    ADD CONSTRAINT approved_matches_pkey PRIMARY KEY (matched_id, user_id);


--
-- Name: avatars avatars_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.avatars
    ADD CONSTRAINT avatars_pkey PRIMARY KEY (id);


--
-- Name: blocked_friends blocked_friends_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.blocked_friends
    ADD CONSTRAINT blocked_friends_pkey PRIMARY KEY (blocked_user_id, gamer_id);


--
-- Name: chat_message chat_message_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.chat_message
    ADD CONSTRAINT chat_message_pkey PRIMARY KEY (id);


--
-- Name: chat_participant chat_participant_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.chat_participant
    ADD CONSTRAINT chat_participant_pkey PRIMARY KEY (room_id, user_id);


--
-- Name: chat_room chat_room_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.chat_room
    ADD CONSTRAINT chat_room_pkey PRIMARY KEY (id);


--
-- Name: coin_ledger coin_ledger_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.coin_ledger
    ADD CONSTRAINT coin_ledger_pkey PRIMARY KEY (id);


--
-- Name: comment_likes_join comment_likes_join_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.comment_likes_join
    ADD CONSTRAINT comment_likes_join_pkey PRIMARY KEY (comment_id, user_id);


--
-- Name: comment comment_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.comment
    ADD CONSTRAINT comment_pkey PRIMARY KEY (comment_id);


--
-- Name: community_members_join community_members_join_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.community_members_join
    ADD CONSTRAINT community_members_join_pkey PRIMARY KEY (community_id, user_id);


--
-- Name: community community_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.community
    ADD CONSTRAINT community_pkey PRIMARY KEY (community_id);


--
-- Name: content_report content_report_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.content_report
    ADD CONSTRAINT content_report_pkey PRIMARY KEY (id);


--
-- Name: cosmetic cosmetic_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.cosmetic
    ADD CONSTRAINT cosmetic_pkey PRIMARY KEY (id);


--
-- Name: declined_matches declined_matches_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.declined_matches
    ADD CONSTRAINT declined_matches_pkey PRIMARY KEY (declined_id, user_id);


--
-- Name: friends friends_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.friends
    ADD CONSTRAINT friends_pkey PRIMARY KEY (friend_id, user_id);


--
-- Name: funnel_event funnel_event_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.funnel_event
    ADD CONSTRAINT funnel_event_pkey PRIMARY KEY (id);


--
-- Name: gamer_badge gamer_badge_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_badge
    ADD CONSTRAINT gamer_badge_pkey PRIMARY KEY (user_id, badge_code);


--
-- Name: gamer_cosmetic gamer_cosmetic_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_cosmetic
    ADD CONSTRAINT gamer_cosmetic_pkey PRIMARY KEY (user_id, cosmetic_id);


--
-- Name: gamer gamer_email_key; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer
    ADD CONSTRAINT gamer_email_key UNIQUE (email);


--
-- Name: gamer_games_join gamer_games_join_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_games_join
    ADD CONSTRAINT gamer_games_join_pkey PRIMARY KEY (game_id, gamer_id);


--
-- Name: gamer_keywords_join gamer_keywords_join_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_keywords_join
    ADD CONSTRAINT gamer_keywords_join_pkey PRIMARY KEY (keyword_id, gamer_id);


--
-- Name: gamer gamer_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer
    ADD CONSTRAINT gamer_pkey PRIMARY KEY (user_id);


--
-- Name: gamer_platform gamer_platform_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_platform
    ADD CONSTRAINT gamer_platform_pkey PRIMARY KEY (user_id, platform);


--
-- Name: gamer gamer_username_key; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer
    ADD CONSTRAINT gamer_username_key UNIQUE (username);


--
-- Name: games games_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.games
    ADD CONSTRAINT games_pkey PRIMARY KEY (game_id);


--
-- Name: keywords keywords_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.keywords
    ADD CONSTRAINT keywords_pkey PRIMARY KEY (id);


--
-- Name: notification_outbox notification_outbox_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.notification_outbox
    ADD CONSTRAINT notification_outbox_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: post_likes_join post_likes_join_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.post_likes_join
    ADD CONSTRAINT post_likes_join_pkey PRIMARY KEY (post_id, user_id);


--
-- Name: post post_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.post
    ADD CONSTRAINT post_pkey PRIMARY KEY (post_id);


--
-- Name: purchase purchase_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.purchase
    ADD CONSTRAINT purchase_pkey PRIMARY KEY (id);


--
-- Name: recommendation_impression recommendation_impression_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.recommendation_impression
    ADD CONSTRAINT recommendation_impression_pkey PRIMARY KEY (id);


--
-- Name: rewarded_ad_grant rewarded_ad_grant_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.rewarded_ad_grant
    ADD CONSTRAINT rewarded_ad_grant_pkey PRIMARY KEY (transaction_id);


--
-- Name: session session_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.session
    ADD CONSTRAINT session_pkey PRIMARY KEY (id);


--
-- Name: session session_token_hash_key; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.session
    ADD CONSTRAINT session_token_hash_key UNIQUE (token_hash);


--
-- Name: chat_room uk_chat_room_pair; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.chat_room
    ADD CONSTRAINT uk_chat_room_pair UNIQUE (pair_key);


--
-- Name: purchase uk_purchase_store_transaction; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.purchase
    ADD CONSTRAINT uk_purchase_store_transaction UNIQUE (platform, store_transaction_id);


--
-- Name: unlocked_admirer unlocked_admirer_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.unlocked_admirer
    ADD CONSTRAINT unlocked_admirer_pkey PRIMARY KEY (user_id, admirer_id);


--
-- Name: content_report uq_report_once_per_reporter; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.content_report
    ADD CONSTRAINT uq_report_once_per_reporter UNIQUE (content_type, content_id, reporter_id);


--
-- Name: verification_code verification_code_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.verification_code
    ADD CONSTRAINT verification_code_pkey PRIMARY KEY (id);


--
-- Name: waiting_friends waiting_friends_pkey; Type: CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.waiting_friends
    ADD CONSTRAINT waiting_friends_pkey PRIMARY KEY (requested_id, user_id);


--
-- Name: idx_chat_message_reported; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_chat_message_reported ON gamebuddy.chat_message USING btree (reported_at);


--
-- Name: idx_chat_message_room_time; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_chat_message_room_time ON gamebuddy.chat_message USING btree (room_id, created_at DESC, id DESC);


--
-- Name: idx_chat_participant_user; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_chat_participant_user ON gamebuddy.chat_participant USING btree (user_id);


--
-- Name: idx_coin_ledger_time; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_coin_ledger_time ON gamebuddy.coin_ledger USING btree (created_at);


--
-- Name: idx_coin_ledger_user; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_coin_ledger_user ON gamebuddy.coin_ledger USING btree (user_id, created_at DESC);


--
-- Name: idx_cosmetic_asset_key; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE UNIQUE INDEX idx_cosmetic_asset_key ON gamebuddy.cosmetic USING btree (asset_key);


--
-- Name: idx_cosmetic_kind; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_cosmetic_kind ON gamebuddy.cosmetic USING btree (kind, sort_order);


--
-- Name: idx_declined_user_time; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_declined_user_time ON gamebuddy.declined_matches USING btree (user_id, declined_at);


--
-- Name: idx_funnel_event_kind; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_funnel_event_kind ON gamebuddy.funnel_event USING btree (kind, created_at);


--
-- Name: idx_funnel_event_user; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_funnel_event_user ON gamebuddy.funnel_event USING btree (user_id, kind);


--
-- Name: idx_gamer_avatar_pending; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_gamer_avatar_pending ON gamebuddy.gamer USING btree (last_modified_date) WHERE ((avatar_status)::text = 'PENDING'::text);


--
-- Name: idx_gamer_badge_showcase; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE UNIQUE INDEX idx_gamer_badge_showcase ON gamebuddy.gamer_badge USING btree (user_id, showcase_slot) WHERE (showcase_slot IS NOT NULL);


--
-- Name: idx_gamer_boost_active; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_gamer_boost_active ON gamebuddy.gamer USING btree (country, boost_expires_at) WHERE (boost_expires_at IS NOT NULL);


--
-- Name: idx_gamer_cohort; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_gamer_cohort ON gamebuddy.gamer USING btree (like_cap_cohort, created_date);


--
-- Name: idx_gamer_cosmetic_user; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_gamer_cosmetic_user ON gamebuddy.gamer_cosmetic USING btree (user_id);


--
-- Name: idx_gamer_dormant; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_gamer_dormant ON gamebuddy.gamer USING btree (last_active_at) WHERE ((deleted_at IS NULL) AND reminders_enabled);


--
-- Name: idx_gamer_fcm_token; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE UNIQUE INDEX idx_gamer_fcm_token ON gamebuddy.gamer USING btree (fcm_token) WHERE (fcm_token IS NOT NULL);


--
-- Name: idx_gamer_platform_platform; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_gamer_platform_platform ON gamebuddy.gamer_platform USING btree (platform);


--
-- Name: idx_gamer_username_lower; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE UNIQUE INDEX idx_gamer_username_lower ON gamebuddy.gamer USING btree (lower((username)::text));


--
-- Name: idx_impression_served_at; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_impression_served_at ON gamebuddy.recommendation_impression USING btree (served_at);


--
-- Name: idx_impression_user_candidate; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_impression_user_candidate ON gamebuddy.recommendation_impression USING btree (user_id, candidate_id);


--
-- Name: idx_notification_recipient; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_notification_recipient ON gamebuddy.notifications USING btree (recipient, created_date);


--
-- Name: idx_outbox_pending; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_outbox_pending ON gamebuddy.notification_outbox USING btree (next_attempt_at, created_at) WHERE (sent_at IS NULL);


--
-- Name: idx_post_community; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_post_community ON gamebuddy.post USING btree (community_id);


--
-- Name: idx_post_updated; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_post_updated ON gamebuddy.post USING btree (updated_date);


--
-- Name: idx_purchase_user; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_purchase_user ON gamebuddy.purchase USING btree (user_id);


--
-- Name: idx_report_status; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_report_status ON gamebuddy.content_report USING btree (status, created_at);


--
-- Name: idx_rewarded_ad_grant_user; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_rewarded_ad_grant_user ON gamebuddy.rewarded_ad_grant USING btree (user_id, created_at DESC);


--
-- Name: idx_session_email; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_session_email ON gamebuddy.session USING btree (email);


--
-- Name: idx_unlocked_admirer_user; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_unlocked_admirer_user ON gamebuddy.unlocked_admirer USING btree (user_id);


--
-- Name: idx_verification_code_email; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_verification_code_email ON gamebuddy.verification_code USING btree (email);


--
-- Name: avatars set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.avatars FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: chat_participant set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.chat_participant FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: chat_room set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.chat_room FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: comment set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.comment FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: community set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.community FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: content_report set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.content_report FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: cosmetic set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.cosmetic FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: gamer_badge set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.gamer_badge FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: gamer_cosmetic set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.gamer_cosmetic FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: games set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.games FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: keywords set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.keywords FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: notification_outbox set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.notification_outbox FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: notifications set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.notifications FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: post set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.post FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: purchase set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.purchase FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: session set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.session FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: verification_code set_updated_at; Type: TRIGGER; Schema: gamebuddy; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON gamebuddy.verification_code FOR EACH ROW EXECUTE FUNCTION gamebuddy.set_updated_at();


--
-- Name: gamer_keywords_join fk24su9eu5fhblvx77oiwxcfcog; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_keywords_join
    ADD CONSTRAINT fk24su9eu5fhblvx77oiwxcfcog FOREIGN KEY (gamer_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: post_likes_join fk2cbg048ocdq9838hny4h6g97t; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.post_likes_join
    ADD CONSTRAINT fk2cbg048ocdq9838hny4h6g97t FOREIGN KEY (post_id) REFERENCES gamebuddy.post(post_id);


--
-- Name: blocked_friends fk3729u0vtmao7yrpdckcnnmhnl; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.blocked_friends
    ADD CONSTRAINT fk3729u0vtmao7yrpdckcnnmhnl FOREIGN KEY (gamer_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: gamer_games_join fk3gk1w5gqk75yywa9cknfx696m; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_games_join
    ADD CONSTRAINT fk3gk1w5gqk75yywa9cknfx696m FOREIGN KEY (gamer_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: waiting_friends fk6fvyihyp8dh9f52b1e68lx9q; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.waiting_friends
    ADD CONSTRAINT fk6fvyihyp8dh9f52b1e68lx9q FOREIGN KEY (requested_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: community_members_join fk7g4in20nl8dvxop2yhbn832o5; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.community_members_join
    ADD CONSTRAINT fk7g4in20nl8dvxop2yhbn832o5 FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: comment_likes_join fk84if1k3dinptdrn5w4xpawq71; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.comment_likes_join
    ADD CONSTRAINT fk84if1k3dinptdrn5w4xpawq71 FOREIGN KEY (comment_id) REFERENCES gamebuddy.comment(comment_id);


--
-- Name: approved_matches fk90benjck2k5ajevwihje43fb3; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.approved_matches
    ADD CONSTRAINT fk90benjck2k5ajevwihje43fb3 FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: approved_matches fk94qu8njwgm3u155d7hnpq8esh; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.approved_matches
    ADD CONSTRAINT fk94qu8njwgm3u155d7hnpq8esh FOREIGN KEY (matched_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: gamer_platform fk_gamer_platform_gamer; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_platform
    ADD CONSTRAINT fk_gamer_platform_gamer FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id) ON DELETE CASCADE;


--
-- Name: friends fkbopmktch8gic3oxk7t7ec4cb6; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.friends
    ADD CONSTRAINT fkbopmktch8gic3oxk7t7ec4cb6 FOREIGN KEY (friend_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: gamer_games_join fkdqmlq0v36v2w3ys5gsupnq9dd; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_games_join
    ADD CONSTRAINT fkdqmlq0v36v2w3ys5gsupnq9dd FOREIGN KEY (game_id) REFERENCES gamebuddy.games(game_id);


--
-- Name: gamer_keywords_join fkev7uual80ew9y4txak0aup4ap; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_keywords_join
    ADD CONSTRAINT fkev7uual80ew9y4txak0aup4ap FOREIGN KEY (keyword_id) REFERENCES gamebuddy.keywords(id);


--
-- Name: community_members_join fkgoxmn2hmjpb2qk3r1u87p4b31; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.community_members_join
    ADD CONSTRAINT fkgoxmn2hmjpb2qk3r1u87p4b31 FOREIGN KEY (community_id) REFERENCES gamebuddy.community(community_id);


--
-- Name: waiting_friends fkjlu43y5hnj4vupw0l9esgji3q; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.waiting_friends
    ADD CONSTRAINT fkjlu43y5hnj4vupw0l9esgji3q FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: blocked_friends fkm34618kkq5g1v4brf2sdqopt3; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.blocked_friends
    ADD CONSTRAINT fkm34618kkq5g1v4brf2sdqopt3 FOREIGN KEY (blocked_user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: post fkokm06ignilxux2n1anwepgun7; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.post
    ADD CONSTRAINT fkokm06ignilxux2n1anwepgun7 FOREIGN KEY (community_id) REFERENCES gamebuddy.community(community_id);


--
-- Name: post_likes_join fkqv87tmyekthrymv5w9jsevesq; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.post_likes_join
    ADD CONSTRAINT fkqv87tmyekthrymv5w9jsevesq FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: comment fks1slvnkuemjsq2kj4h3vhx7i1; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.comment
    ADD CONSTRAINT fks1slvnkuemjsq2kj4h3vhx7i1 FOREIGN KEY (post_id) REFERENCES gamebuddy.post(post_id);


--
-- Name: comment_likes_join fksakra0l1lqrow56n3pvjoeqcw; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.comment_likes_join
    ADD CONSTRAINT fksakra0l1lqrow56n3pvjoeqcw FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: friends fkt9hovkymyt454v5k1pkndyryx; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.friends
    ADD CONSTRAINT fkt9hovkymyt454v5k1pkndyryx FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: community fktjyro66mpf7ydi9w35ejrbqjp; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.community
    ADD CONSTRAINT fktjyro66mpf7ydi9w35ejrbqjp FOREIGN KEY (owner) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: gamer_badge gamer_badge_user_id_fkey; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_badge
    ADD CONSTRAINT gamer_badge_user_id_fkey FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: gamer_cosmetic gamer_cosmetic_cosmetic_id_fkey; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_cosmetic
    ADD CONSTRAINT gamer_cosmetic_cosmetic_id_fkey FOREIGN KEY (cosmetic_id) REFERENCES gamebuddy.cosmetic(id);


--
-- Name: gamer_cosmetic gamer_cosmetic_user_id_fkey; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer_cosmetic
    ADD CONSTRAINT gamer_cosmetic_user_id_fkey FOREIGN KEY (user_id) REFERENCES gamebuddy.gamer(user_id);


--
-- Name: gamer gamer_equipped_banner_id_fkey; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer
    ADD CONSTRAINT gamer_equipped_banner_id_fkey FOREIGN KEY (equipped_banner_id) REFERENCES gamebuddy.cosmetic(id);


--
-- Name: gamer gamer_equipped_frame_id_fkey; Type: FK CONSTRAINT; Schema: gamebuddy; Owner: -
--

ALTER TABLE ONLY gamebuddy.gamer
    ADD CONSTRAINT gamer_equipped_frame_id_fkey FOREIGN KEY (equipped_frame_id) REFERENCES gamebuddy.cosmetic(id);


--
-- PostgreSQL database dump complete
--


