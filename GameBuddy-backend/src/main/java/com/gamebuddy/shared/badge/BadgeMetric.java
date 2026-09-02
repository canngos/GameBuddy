package com.gamebuddy.shared.badge;

/**
 * The things a badge or a mission can be earned by doing.
 *
 * <p>Every mission is "some metric reached some number", so this is the whole vocabulary
 * of what the app is able to measure about a gamer. A new mission over an existing metric
 * costs one line; a new mission over something nobody counts yet needs a value here and a
 * {@link BadgeMetricSource} that produces it.
 *
 * <p>Deliberately in {@code shared}: the missions live in the profile module, but the
 * counting happens wherever the data does — messages in {@code match}, lobbies in
 * {@code lobby} — and neither of those may depend on profile.
 *
 * <h2>Cumulative and standing metrics are not interchangeable</h2>
 *
 * <p>A <em>badge</em> asks "is this gamer at N?", so either kind works. A <em>mission</em>
 * asks "have they done N more since this set was drawn", which it answers by subtracting a
 * baseline — and that only means anything for a number that never goes down. A standing
 * metric that falls below its baseline leaves a mission stuck at zero until the gamer wins
 * the ground back, which reads as a bug and cannot be told apart from one.
 *
 * <p>So the standing metrics below are marked, and {@code Mission} must not use them. The
 * cumulative twin exists where a mission wanted one: {@link #LOBBIES_JOINED} is standing
 * because "how many teams am I in" is the interesting question for a badge, and
 * {@link #LOBBIES_JOINED_EVER} is its history for missions.
 */
public enum BadgeMetric {

    // --- people ------------------------------------------------------------

    /** Mutual matches. One-sided likes do not count. */
    MATCHES,

    /** Yes-swipes sent, answered or not. Cumulative, where {@link #MATCHES} needs two people. */
    LIKES_SENT,

    /** Super likes sent. One per person, so this counts people rather than taps. */
    SUPER_LIKES_SENT,

    /** Accepted friendships. <strong>Standing</strong> — unfriending gives one back. */
    FRIENDS,

    // --- talking -----------------------------------------------------------

    /** Chat messages this gamer has sent, over all rooms. Lobby chat is not counted here. */
    MESSAGES_SENT,

    /** Messages sent in lobby chat. A different room and a different mission. */
    LOBBY_MESSAGES_SENT,

    // --- lobbies -----------------------------------------------------------

    /**
     * Lobby teams this gamer is part of — opened, or accepted into. <strong>Standing</strong>,
     * not history: leaving or being removed un-counts it.
     *
     * <p>Replaced COMMUNITIES_JOINED and POSTS_WRITTEN when the Community feature retired;
     * the badges over those metrics were retired with it (earned rows survive — see
     * {@code Badge.byCode}).
     */
    LOBBIES_JOINED,

    /** Every lobby ever joined, including ones since left. The cumulative twin, for missions. */
    LOBBIES_JOINED_EVER,

    /** Lobbies opened. Owning one is a different act from joining somebody else's. */
    LOBBIES_CREATED,

    // --- the shelf ---------------------------------------------------------

    /** Frames and banners bought. Free ones do not count — nothing was achieved. */
    COSMETICS_OWNED,

    /**
     * How many of the two cosmetic slots are filled. 0, 1 or 2.
     * <strong>Standing</strong>, obviously — taking a frame off is allowed.
     */
    COSMETICS_WORN,

    /** Coins spent, lifetime, as a positive number. Read off the ledger, not off the balance. */
    COINS_SPENT,

    // --- turning up --------------------------------------------------------

    /**
     * Daily rewards claimed, lifetime. Distinct from {@link #DAILY_STREAK}: this one
     * remembers every day a gamer showed up, where the streak forgets the moment they miss.
     */
    DAILY_CLAIMS,

    /** The current unbroken run of daily claims. <strong>Standing</strong> — a miss resets it. */
    DAILY_STREAK,

    /** Rewarded videos watched to completion, lifetime. */
    ADS_WATCHED,

    // --- the app itself ----------------------------------------------------

    /**
     * How much of the profile is filled in, out of three: a picture, three games, three
     * keywords. A count rather than a boolean so the badge can show partial progress.
     * <strong>Standing</strong>: emptying your games list takes it back.
     */
    PROFILE_COMPLETENESS,

    /**
     * Mission sets finished — all three claimed. The set currently in play does not count.
     *
     * <p>Its only reader is the badge for finishing the campaign, and it exists so that
     * badge needs no special case: without it, the one award in the game that is not "a
     * number reached a threshold" would need a second granting path beside
     * {@code DefaultBadgeService.evaluate}, with its own copy of the once-only guarantee
     * and its own notification.
     */
    MISSION_SETS_DONE,

    /**
     * Badges earned. The one metric that measures the system it belongs to, which is what
     * lets a badge be awarded for collecting the others.
     *
     * <p>Counts rows rather than live catalogue entries, so a retired badge still counts
     * towards it. That is deliberate: it was earned, and taking it back years later because
     * a feature was removed would be a strange thing to do to somebody.
     */
    BADGES_EARNED
}
