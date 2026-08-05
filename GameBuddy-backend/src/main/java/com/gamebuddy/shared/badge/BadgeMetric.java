package com.gamebuddy.shared.badge;

/**
 * The things a badge can be earned by doing.
 *
 * <p>Every mission is "some metric reached some number", so this is the whole vocabulary
 * of what the app is able to measure about a gamer. A new mission over an existing metric
 * costs one line; a new mission over something nobody counts yet needs a value here and a
 * {@link BadgeMetricSource} that produces it.
 *
 * <p>Deliberately in {@code shared}: the missions live in the profile module, but the
 * counting happens wherever the data does — messages in {@code match}, posts in
 * {@code community} — and neither of those may depend on profile.
 */
public enum BadgeMetric {

    /** Mutual matches. One-sided likes do not count. */
    MATCHES,

    /** Accepted friendships. */
    FRIENDS,

    /** Chat messages this gamer has sent, over all rooms. */
    MESSAGES_SENT,

    /** Communities this gamer is a member of, including ones they own. */
    COMMUNITIES_JOINED,

    /** Posts written, in any community. */
    POSTS_WRITTEN,

    /** Frames and banners bought. Free ones do not count — nothing was achieved. */
    COSMETICS_OWNED,

    /** How many of the two cosmetic slots are filled. 0, 1 or 2. */
    COSMETICS_WORN,

    /**
     * How much of the profile is filled in, out of three: a picture, three games, three
     * keywords. A count rather than a boolean so the badge can show partial progress.
     */
    PROFILE_COMPLETENESS
}
