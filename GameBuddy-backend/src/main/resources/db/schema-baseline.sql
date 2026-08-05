--
-- GameBuddy baseline schema.
--
-- Creates an empty database from nothing. Apply this first, then any upgrade-*.sql newer
-- than it, in filename order.
--
-- Generated from the JPA entities and then committed, rather than left to Hibernate at
-- startup. The point is that both environments can run ddl-auto=validate: the schema is
-- built by SQL that can be reviewed and rolled forward, and the application's job is to
-- refuse to start if what it finds does not match what it expects. With ddl-auto=update
-- the application silently reshapes the database instead, which is convenient exactly
-- until it quietly diverges from production.
--
-- Regenerate with: see README, "Regenerating the baseline schema".
--
-- One thing this file carries that the entities cannot: idx_outbox_pending is partial.
--

--
-- PostgreSQL database dump
--

\restrict hw9djYOdNyxWzYtlIfuQLXPVzyHcGxDobY96IoFKoAz99JaByvJfyoitwnElx1U

-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

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


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: approved_matches; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.approved_matches (
    matched_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: avatars; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.avatars (
    id uuid NOT NULL,
    image character varying(255)
);


--
-- Name: blocked_friends; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.blocked_friends (
    blocked_user_id character varying(255) NOT NULL,
    gamer_id character varying(255) NOT NULL
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
    user_id character varying(255) NOT NULL
);


--
-- Name: chat_room; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.chat_room (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    pair_key character varying(512) NOT NULL
);


--
-- Name: comment; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.comment (
    created_date timestamp(6) with time zone NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    comment_id uuid NOT NULL,
    post_id uuid NOT NULL,
    message character varying(255),
    owner character varying(255)
);


--
-- Name: comment_likes_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.comment_likes_join (
    comment_id uuid NOT NULL,
    user_id character varying(255) NOT NULL
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
    wallpaper character varying(255)
);


--
-- Name: community_members_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.community_members_join (
    community_id uuid NOT NULL,
    user_id character varying(255) NOT NULL
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
    CONSTRAINT content_report_content_type_check CHECK (((content_type)::text = ANY (ARRAY[('POST'::character varying)::text, ('COMMENT'::character varying)::text]))),
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
    CONSTRAINT cosmetic_kind_check CHECK (((kind)::text = ANY ((ARRAY['FRAME'::character varying, 'BANNER'::character varying])::text[]))),
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
    user_id character varying(255) NOT NULL
);


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
    CONSTRAINT gamer_avatar_status_check CHECK (((avatar_status IS NULL) OR ((avatar_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))),
    CONSTRAINT gamer_role_check CHECK (((role)::text = ANY (ARRAY[('USER'::character varying)::text, ('ADMIN'::character varying)::text]))),
    CONSTRAINT gamer_subscription_tier_check CHECK (((subscription_tier)::text = ANY (ARRAY[('BASIC'::character varying)::text, ('GOLD'::character varying)::text])))
);


--
-- Name: gamer_badge; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_badge (
    user_id character varying(255) NOT NULL,
    badge_code character varying(48) NOT NULL,
    earned_at timestamp with time zone DEFAULT now() NOT NULL,
    collected_at timestamp with time zone,
    showcase_slot integer,
    CONSTRAINT gamer_badge_slot_check CHECK (((showcase_slot >= 0) AND (showcase_slot <= 2)))
);


--
-- Name: gamer_cosmetic; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_cosmetic (
    user_id character varying(255) NOT NULL,
    cosmetic_id uuid NOT NULL,
    paid integer DEFAULT 0 NOT NULL,
    acquired_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: gamer_games_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_games_join (
    game_id character varying(255) NOT NULL,
    gamer_id character varying(255) NOT NULL
);


--
-- Name: gamer_keywords_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.gamer_keywords_join (
    keyword_id uuid NOT NULL,
    gamer_id character varying(255) NOT NULL
);


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
    game_name character varying(255)
);


--
-- Name: keywords; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.keywords (
    created_date timestamp(6) with time zone,
    id uuid NOT NULL,
    description character varying(255),
    keyword_name character varying(255)
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
    body character varying(1000) NOT NULL
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
    title character varying(255) NOT NULL
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
    title character varying(255)
);


--
-- Name: post_likes_join; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.post_likes_join (
    post_id uuid NOT NULL,
    user_id character varying(255) NOT NULL
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
    CONSTRAINT purchase_platform_check CHECK (((platform)::text = ANY (ARRAY[('APPLE_APP_STORE'::character varying)::text, ('GOOGLE_PLAY'::character varying)::text]))),
    CONSTRAINT purchase_status_check CHECK (((status)::text = ANY (ARRAY[('GRANTED'::character varying)::text, ('REFUNDED'::character varying)::text])))
);


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
-- Name: session; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.session (
    created_date timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    email character varying(255) NOT NULL
);


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
    email character varying(255) NOT NULL
);


--
-- Name: waiting_friends; Type: TABLE; Schema: gamebuddy; Owner: -
--

CREATE TABLE gamebuddy.waiting_friends (
    requested_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
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
-- Name: idx_gamer_avatar_pending; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_gamer_avatar_pending ON gamebuddy.gamer USING btree (last_modified_date) WHERE ((avatar_status)::text = 'PENDING'::text);


--
-- Name: idx_gamer_badge_showcase; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE UNIQUE INDEX idx_gamer_badge_showcase ON gamebuddy.gamer_badge USING btree (user_id, showcase_slot) WHERE (showcase_slot IS NOT NULL);


--
-- Name: idx_gamer_cosmetic_user; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_gamer_cosmetic_user ON gamebuddy.gamer_cosmetic USING btree (user_id);


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
-- Name: idx_session_email; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_session_email ON gamebuddy.session USING btree (email);


--
-- Name: idx_verification_code_email; Type: INDEX; Schema: gamebuddy; Owner: -
--

CREATE INDEX idx_verification_code_email ON gamebuddy.verification_code USING btree (email);


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

\unrestrict hw9djYOdNyxWzYtlIfuQLXPVzyHcGxDobY96IoFKoAz99JaByvJfyoitwnElx1U

