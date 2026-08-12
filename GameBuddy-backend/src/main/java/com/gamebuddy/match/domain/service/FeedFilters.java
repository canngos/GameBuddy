package com.gamebuddy.match.domain.service;

import com.gamebuddy.common.enums.Platform;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * What a gamer asked to narrow the deck to.
 *
 * <p>A Gold entitlement, and the most gamer-native one available: "only show me people who
 * play this, near me, who are on right now" is worth paying for to somebody who wants a
 * squad tonight, in a way that "more likes" is not.
 *
 * <p>Every field is optional and null means "do not narrow by this". An instance where all
 * of them are null is {@link #none()} and is not a filtered request at all — which matters,
 * because a free account is allowed to make an unfiltered one.
 *
 * <p><b>An account that has not said what it plays on is never excluded by the platform
 * filter.</b> Accounts predating the field have an empty set, and reading silence as "not
 * on your platform" would hide people who may well be — punishing them for the timing of
 * their signup. The filter narrows to people who said yes, plus people who said nothing;
 * it never narrows to people who said no, because nobody has.
 */
public record FeedFilters(String gameId, String country, Boolean onlineNow, Platform platform) {

    /**
     * How recently somebody must have been active to count as online.
     *
     * <p>Generous on purpose. The alternative is a live socket check, which would make the
     * filter mean "has the app open this second" — true far less often than somebody is
     * actually available to play, and it would make the filter look broken at exactly the
     * hours it should be most useful.
     */
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(15);

    public static FeedFilters none() {
        return new FeedFilters(null, null, null, null);
    }

    /**
     * The instant a candidate must have been active since to count as online, or null when
     * the filter is off.
     *
     * <p>Exists so the database query that pre-excludes candidates and the in-memory
     * {@link #matches} check cannot drift apart about what "online" means. Two copies of
     * fifteen minutes would eventually become fourteen and sixteen.
     */
    public Instant activeSince(Clock clock) {
        return Boolean.TRUE.equals(onlineNow) ? clock.instant().minus(ONLINE_WINDOW) : null;
    }

    /** Whether this asks for anything at all. Only a narrowed feed needs the entitlement. */
    public boolean narrowing() {
        return gameId != null || country != null || Boolean.TRUE.equals(onlineNow) || platform != null;
    }

    /** Whether a candidate survives every filter that was actually set. */
    public boolean matches(Gamer candidate, Clock clock) {
        if (gameId != null && candidate.getLikedgames().stream().noneMatch(game -> gameId.equals(game.getGameId()))) {
            return false;
        }
        if (country != null && !country.equalsIgnoreCase(candidate.getCountry())) {
            return false;
        }
        Instant activeSince = activeSince(clock);
        if (activeSince != null) {
            Instant lastActive = candidate.getLastActiveAt();
            if (lastActive == null || lastActive.isBefore(activeSince)) {
                return false;
            }
        }
        // An intersection, not an equality: a gamer holds several platforms and matching
        // any one of them is enough to play together. The empty set passes — see the class
        // comment on why silence must not be read as a no.
        if (platform != null
                && !candidate.getPlatforms().isEmpty()
                && !candidate.getPlatforms().contains(platform)) {
            return false;
        }
        return true;
    }
}
