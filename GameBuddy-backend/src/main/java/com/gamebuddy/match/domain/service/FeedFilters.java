package com.gamebuddy.match.domain.service;

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
 * <p><b>Platform is missing on purpose.</b> The strategy names it, and the profile has no
 * such field — there is nothing to filter on. Adding one is a schema change, an onboarding
 * step and a product decision about whether somebody plays on one platform or several. It
 * is tracked separately rather than guessed at here.
 */
public record FeedFilters(String gameId, String country, Boolean onlineNow) {

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
        return new FeedFilters(null, null, null);
    }

    /** Whether this asks for anything at all. Only a narrowed feed needs the entitlement. */
    public boolean narrowing() {
        return gameId != null || country != null || Boolean.TRUE.equals(onlineNow);
    }

    /** Whether a candidate survives every filter that was actually set. */
    public boolean matches(Gamer candidate, Clock clock) {
        if (gameId != null && candidate.getLikedgames().stream().noneMatch(game -> gameId.equals(game.getGameId()))) {
            return false;
        }
        if (country != null && !country.equalsIgnoreCase(candidate.getCountry())) {
            return false;
        }
        if (Boolean.TRUE.equals(onlineNow)) {
            Instant lastActive = candidate.getLastActiveAt();
            if (lastActive == null || lastActive.isBefore(clock.instant().minus(ONLINE_WINDOW))) {
                return false;
            }
        }
        return true;
    }
}
