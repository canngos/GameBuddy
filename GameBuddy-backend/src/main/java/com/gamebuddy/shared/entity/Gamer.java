package com.gamebuddy.shared.entity;

import com.gamebuddy.common.enums.AgeBand;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.security.RevocableUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * A GameBuddy account. One class, where there were five.
 *
 * <p>Each service previously carried its own {@code Gamer} mapping the same
 * {@code gamer} table, and each mapped a different subset of the columns and
 * relationships. That is why a blocked gamer could still be recommended (match-service did
 * not map {@code blocked_friends} at all) and why a purchased avatar was invisible on the
 * profile screen (the services disagreed about which schema {@code avatars} lived in).
 * Neither bug is expressible now: there is one mapping, and every module reads it.
 *
 * <p>The collections are the union of what the five services mapped. Where only one module
 * writes a collection, that is noted — the mapping is shared, the ownership is not.
 */
@Entity
@Table(name = "gamer")
@Getter
@Setter
@NoArgsConstructor
public class Gamer implements RevocableUser {

    @Id
    private String userId;

    @Column(name = "username", unique = true)
    private String gamerUsername;

    @Column(unique = true, nullable = false)
    private String email;

    private Integer age;
    private String country;
    /**
     * The legacy catalogue avatar: a row in {@code avatars}.
     *
     * <p>Superseded by {@link #avatarKey}. Kept while the catalogue still exists so
     * accounts created before uploads do not lose their picture; it goes when the
     * cosmetics catalogue replaces the paid-avatar table.
     */
    private UUID avatar;

    /**
     * The object key of an uploaded avatar, not a URL.
     *
     * <p>Which host serves it is a deployment concern that changes — r2.dev now, a custom
     * domain later — and storing a URL would bake today's answer into every row.
     */
    private String avatarKey;

    /**
     * NULL when nothing has been uploaded.
     *
     * <p>Only {@link AvatarStatus#APPROVED} is shown to anyone but the owner, which is
     * enforced in {@link #hasVisibleAvatar()} rather than at each of the four call sites
     * that used to resolve an avatar independently.
     */
    @Enumerated(EnumType.STRING)
    private AvatarStatus avatarStatus;

    /**
     * What the classifier scored the current upload, or null if it never answered.
     *
     * <p>The distinction is the whole point, and PENDING alone cannot express it. An image
     * held with a score of 0.4 is genuinely ambiguous and wants a person; an image held
     * with no score at all was never looked at, because the classifier was down — and the
     * queue used to conflate the two, so an outage looked exactly like a flood of
     * borderline photographs. {@code AvatarReviewJob} re-screens the second kind and
     * leaves the first.
     *
     * <p>Kept after the verdict so a moderator can see whether a held image landed at 0.21
     * or 0.84, and so the thresholds can be retuned against real traffic instead of being
     * guessed at a second time.
     */
    @Column(name = "avatar_score")
    private Double avatarScore;

    /**
     * When the current upload arrived.
     *
     * <p>Separate from {@link #lastModifiedDate}, which {@code @UpdateTimestamp} moves on
     * every write to the row — a gamer changing their username would otherwise reset how
     * long their avatar had been waiting, and the queue would show a two-day-old upload as
     * new. The auto-publish deadline needs a clock that only the upload winds.
     */
    @Column(name = "avatar_uploaded_at")
    private Instant avatarUploadedAt;

    private String gender;
    private String pwd;

    /**
     * When the account was created.
     *
     * <p>Nothing set this. {@code register()} never touched it and there was no annotation
     * behind it, so every row in the database had NULL here — which nobody noticed because
     * nothing read it until the console's growth graph did, and a growth graph fed by a
     * column that is always NULL is a flat line that looks like a product with no users.
     *
     * <p>{@code @CreationTimestamp} rather than a line in the registration service: gamers
     * are created on more than one path, and "remember to stamp it" is the instruction that
     * was already forgotten once.
     */
    @CreationTimestamp
    private Instant createdDate;

    @UpdateTimestamp
    private Instant lastModifiedDate;

    @Column(nullable = false)
    private Boolean isBlocked = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean isRegistered = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean isVerified = Boolean.FALSE;

    private String fcmToken;

    @Column(nullable = false)
    private Integer coin = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    /** Tokens minted before this instant are refused. */
    private Instant tokensValidFrom;

    /**
     * When the gamer deleted their own account, or null.
     *
     * <p>The row is kept and anonymised rather than removed: posts, comments and messages
     * reference it. {@link #isEnabled()} returns false once this is set, and the shared JWT
     * filter checks it, so a deleted account stops authenticating everywhere at once.
     */
    private Instant deletedAt;

    // --- Coming back -------------------------------------------------------

    /**
     * The last time this gamer did anything in the app.
     *
     * <p>Updated at most once an hour rather than on every request — see
     * {@code LastActiveFilter}. The re-engagement job only needs to know "days since", so
     * an hour of imprecision costs nothing and saves a write per request.
     */
    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    /** When a "come back" notification was last sent, or null if none ever has been. */
    @Column(name = "last_nudged_at")
    private Instant lastNudgedAt;

    /**
     * How many nudges have been sent since this gamer was last active.
     *
     * <p>Reset to zero the moment they open the app, which is what makes the cap a cap on
     * *this* absence rather than on their lifetime. Without the reset somebody who came
     * back once would never be reminded again.
     */
    @Column(name = "nudge_count", nullable = false)
    private int nudgeCount = 0;

    // --- What may interrupt them -------------------------------------------
    // Four switches, matching NotificationCategory. All default to on, and all can be
    // turned off — including messages. An app that reserves the right to interrupt you
    // about something you said you did not want gets its notifications disabled at the
    // operating system instead, which costs it the ones that mattered.
    //
    // Enforced once, in NotificationDispatcher, rather than at each of the nine places
    // that raise a notification. A check that has to be remembered nine times is a check
    // that will be forgotten once.

    /** Chat messages. */
    @Column(name = "notify_messages", nullable = false)
    private boolean notifyMessages = true;

    /** Matches, friend requests and answers, badges earned. */
    @Column(name = "notify_social", nullable = false)
    private boolean notifySocial = true;

    /** Posts, comments and likes in communities they joined. */
    @Column(name = "notify_communities", nullable = false)
    private boolean notifyCommunities = true;

    /**
     * "Come back" nudges — the re-engagement reminders.
     *
     * <p>On by default, off in one tap. A persuasive system that cannot be told to stop is
     * not persuasion.
     */
    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = true;

    /**
     * Optimistic lock. Coin spending and subscription grants both read-decide-write, and a
     * renewal can race a refund.
     */
    @Version
    private Long version;

    // --- Subscription ------------------------------------------------------
    // Never read the tier alone to decide what a gamer may do:
    // SubscriptionTier.effective(...) also checks the expiry, and a stored tier on its own
    // keeps granting GOLD to everyone who ever subscribed and then cancelled.

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 16)
    private SubscriptionTier subscriptionTier = SubscriptionTier.BASIC;

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    // --- Daily swipe budget ------------------------------------------------
    // One budget with a sub-cap: every decision costs a swipe, and an accept additionally
    // draws on accepts_used. Both counters share one reset instant, so a gamer's day does
    // not start at two different times.

    @Column(name = "swipes_used", nullable = false)
    private int swipesUsed = 0;

    @Column(name = "accepts_used", nullable = false)
    private int acceptsUsed = 0;

    @Column(name = "quota_reset_at")
    private Instant quotaResetAt;

    // --- Taste -------------------------------------------------------------
    // Batch-fetched: rendering a profile touches friends, games, keywords and
    // achievements, and one SELECT per element made that page O(n) queries.

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "gamer_keywords_join",
            joinColumns = @JoinColumn(name = "gamer_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id"))
    private Set<Keywords> keywords = new LinkedHashSet<>();

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "gamer_games_join",
            joinColumns = @JoinColumn(name = "gamer_id"),
            inverseJoinColumns = @JoinColumn(name = "game_id"))
    private Set<Games> likedgames = new LinkedHashSet<>();

    /**
     * When the two collections above last changed, or null if the trained model is still a
     * fair description of this gamer.
     *
     * <p>The recommender is trained offline and ranks a known gamer from features frozen
     * into the artefact at training time, so editing games or keywords used to change
     * nothing about who that gamer was shown until the next retrain — the feed kept
     * answering the profile they no longer had. Non-null here means "the artefact's copy of
     * this profile is out of date", and the feed ranks them from the live profile instead.
     *
     * <p>A timestamp rather than a boolean so it can be cleared safely: a retrain only
     * supersedes the edits it actually saw, and one arriving while the export is running
     * must survive it. Nothing clears it yet — there is no automated retrain to clear it on
     * — which is why the column is written but never reset. See
     * {@code RecommenderStalenessListener}.
     */
    @Column(name = "recommender_profile_changed_at")
    private Instant recommenderProfileChangedAt;

    // --- Social ------------------------------------------------------------

    /** Written by the profile module; friendship follows a match. */
    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "friends",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "friend_id"))
    private Set<Gamer> friends = new LinkedHashSet<>();

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "waiting_friends",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "requested_id"))
    private Set<Gamer> waitingFriends = new LinkedHashSet<>();

    /**
     * Who this gamer has blocked.
     *
     * <p>match-service did not map this at all, so a blocked gamer was still recommended
     * and a pair who had matched before one blocked the other could carry on chatting. The
     * block is honoured wherever a pairing is acted on rather than being materialised into
     * the match tables, so it takes effect immediately and needs no cleanup.
     */
    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "blocked_friends",
            joinColumns = @JoinColumn(name = "gamer_id"),
            inverseJoinColumns = @JoinColumn(name = "blocked_user_id"))
    private Set<Gamer> blockedFriends = new LinkedHashSet<>();

    /** Swiped yes on. Written by the match module. */
    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "approved_matches",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "matched_id"))
    private Set<Gamer> approvedMatches = new LinkedHashSet<>();

    // Declines are not mapped here. They carry a timestamp, because a pass expires rather
    // than hiding somebody forever, and a @ManyToMany join table cannot hold one. The match
    // module owns them as DeclinedMatch — which also keeps a mapping only one module uses
    // out of the shared foundation.

    // --- Collectibles ------------------------------------------------------

    // Earned badges are not mapped here. They were two @ManyToMany sets over an
    // `achievements` catalogue table — earned, and collected — which meant loading a gamer
    // for any reason could drag their whole trophy cabinet along, and "has this been
    // claimed" was a membership test against a second collection rather than a fact about
    // the first. GamerBadge holds one row per earned badge with the claim as a timestamp
    // on it, and the profile module reads them when something actually wants them.

    // Bought avatars are gone. Avatars are uploaded now, so the catalogue has nothing left
    // for sale and the join table that recorded those purchases went with it. What is worn
    // rather than owned lives below.

    // --- Worn ---------------------------------------------------------------

    /**
     * The frame drawn around this gamer's avatar, or null for none.
     *
     * <p>Nullable columns rather than a join table: a gamer wears at most one of each, and
     * a table would permit states the product does not have — two frames at once, or a
     * banner in the frame slot.
     *
     * <p>Lazy because these are read wherever a gamer is rendered, which is mostly lists.
     * The batching that keeps that from becoming an N+1 is declared on {@link Cosmetic}
     * itself — {@code @BatchSize} is rejected on a to-one attribute, and on the target
     * class it covers both of these slots and any future reference at once.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_frame_id")
    private Cosmetic equippedFrame;

    /** The banner behind this gamer's profile header, or null for none. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_banner_id")
    private Cosmetic equippedBanner;

    // --- UserDetails -------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // The "ROLE_" prefix is required by hasRole(...). One service read plain "USER"
        // while auth-service issued "ROLE_USER", so the two disagreed about one token.
        return List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? Role.USER : role).name()));
    }

    @Override
    public Instant getTokensValidFrom() {
        return tokensValidFrom;
    }

    /**
     * Invalidates every token issued so far for this account.
     *
     * <p>Called on password change, on ban, and on account deletion. Without it a stolen
     * token stayed valid for its full lifetime even after the owner changed their password
     * — which is the one action a user takes precisely because they think it was stolen.
     */
    public void revokeIssuedTokens() {
        this.tokensValidFrom = Instant.now();
    }

    @Override
    public String getPassword() {
        return pwd;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** The account id, so the logs identify the user without recording their address. */
    @Override
    public String getLogIdentifier() {
        return userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Null-safe: a row with a NULL is_blocked threw NullPointerException out of the
        // authentication filter.
        return !Boolean.TRUE.equals(isBlocked);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return deletedAt == null;
    }

    // --- Pairing rules -----------------------------------------------------

    /**
     * True when either side has blocked the other; blocking is symmetric in effect.
     *
     * <p>Both owning collections are read rather than mapping an inverse side, because an
     * inverse collection is only populated inside a Hibernate session — a block set on one
     * instance would be invisible from the other everywhere else.
     */
    public boolean hasBlockRelationshipWith(Gamer other) {
        return blockedFriends.contains(other) || other.getBlockedFriends().contains(this);
    }

    /**
     * Whether the uploaded avatar may be shown to anyone other than its owner.
     *
     * <p>PENDING and REJECTED are both invisible. The distinction between them exists for
     * the owner — a delay and a decision are different things to be told — but from
     * everyone else's side they are the same: fall back to the monogram.
     */
    public boolean hasVisibleAvatar() {
        return avatarKey != null && avatarStatus == AvatarStatus.APPROVED;
    }

    /**
     * Whether these two have matched — both swiped yes on each other.
     *
     * <p>The precondition for a friend request and for chat. A one-sided swipe is not a
     * match, and treating it as one is how a stranger could open a conversation with
     * someone who had not agreed to it.
     */
    public boolean isMatchedWith(Gamer other) {
        return approvedMatches.contains(other) && other.getApprovedMatches().contains(this);
    }

    /**
     * Whether this account is part of the population at all.
     *
     * <p>False for moderators. The moderator account exists to review reports and ban
     * people, not to be swiped on: it has no age, no games and no keywords, so it cannot
     * be ranked, and a staff account appearing in the deck is both a privacy problem for
     * whoever holds it and an obvious target for anyone who works out what it is.
     *
     * <p>Checked as a property of the account rather than enforced at each screen, because
     * "everywhere a gamer can be seen" is a list that grows. The three native queries that
     * cannot call this repeat the rule in SQL; they are the only other way into the
     * population.
     */
    public boolean isDiscoverable() {
        return role != Role.ADMIN;
    }

    /** Whether this gamer may be shown, matched with, or chat with {@code other}. */
    public boolean isPairableWith(Gamer other) {
        return other.isDiscoverable()
                && !hasBlockRelationshipWith(other)
                && other.isAccountNonLocked()
                && other.isEnabled()
                && AgeBand.compatible(age, other.getAge());
    }

    // --- Identity ----------------------------------------------------------
    // By id only. The collections above are Sets of Gamer, so equals and hashCode are on
    // the hot path for every contains() check; anything touching a lazy association would
    // initialise it, and anything touching a mutable field would break the Set contract
    // when that field changed.

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Gamer other)) {
            return false;
        }
        return userId != null && userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return userId == null ? 0 : userId.hashCode();
    }
}
