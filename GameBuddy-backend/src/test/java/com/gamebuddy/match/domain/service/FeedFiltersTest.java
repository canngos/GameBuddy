package com.gamebuddy.match.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.common.enums.Platform;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.Games;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FeedFilters")
class FeedFiltersTest {

    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private Gamer gamer(String country, Instant lastActive, String... gameIds) {
        Gamer g = new Gamer();
        g.setCountry(country);
        g.setLastActiveAt(lastActive);
        Set<Games> games = new LinkedHashSet<>();
        for (String id : gameIds) {
            Games game = new Games();
            game.setGameId(id);
            games.add(game);
        }
        g.setLikedgames(games);
        return g;
    }

    private Gamer on(Platform... platforms) {
        Gamer g = gamer("FI", NOW);
        g.setPlatforms(new LinkedHashSet<>(Set.of(platforms)));
        return g;
    }

    @Nested
    @DisplayName("narrowing")
    class Narrowing {

        @Test
        @DisplayName("none() asks for nothing, so it costs nothing")
        void noneIsNotNarrowing() {
            assertFalse(FeedFilters.none().narrowing());
        }

        @Test
        @DisplayName("onlineNow false is not a filter — it is the absence of one")
        void explicitFalseIsNotNarrowing() {
            // A client that sends onlineNow=false has asked for the ordinary feed. Treating
            // that as a filter would charge a free account for a request it is entitled to.
            assertFalse(new FeedFilters(null, null, false, null).narrowing());
        }

        @Test
        void anySetFieldNarrows() {
            assertTrue(new FeedFilters("game-1", null, null, null).narrowing());
            assertTrue(new FeedFilters(null, "FI", null, null).narrowing());
            assertTrue(new FeedFilters(null, null, true, null).narrowing());
            assertTrue(new FeedFilters(null, null, null, Platform.PC).narrowing());
        }
    }

    @Nested
    @DisplayName("activeSince")
    class ActiveSince {

        @Test
        @DisplayName("null unless the online filter is actually on")
        void nullWhenNotFiltering() {
            assertNull(FeedFilters.none().activeSince(clock));
            assertNull(new FeedFilters(null, null, false, null).activeSince(clock));
        }

        @Test
        @DisplayName("the cutoff the database is given is the same one matches() uses")
        void agreesWithMatches() {
            // These two run in different places — one in SQL to decide who gets ranked,
            // one in memory to decide who gets shown — and a disagreement between them
            // would show offline people under an "online now" filter.
            FeedFilters filters = new FeedFilters(null, null, true, null);
            Instant cutoff = filters.activeSince(clock);
            assertEquals(NOW.minusSeconds(15 * 60), cutoff);

            // A candidate exactly on the boundary is in, one a second earlier is out.
            assertTrue(filters.matches(gamer("FI", cutoff), clock));
            assertFalse(filters.matches(gamer("FI", cutoff.minusSeconds(1)), clock));
        }
    }

    @Nested
    @DisplayName("matching")
    class Matching {

        @Test
        void gameMustBeOneTheyPlay() {
            Gamer candidate = gamer("FI", NOW, "game-1", "game-2");
            assertTrue(new FeedFilters("game-2", null, null, null).matches(candidate, clock));
            assertFalse(new FeedFilters("game-9", null, null, null).matches(candidate, clock));
        }

        @Test
        @DisplayName("country is compared case-insensitively")
        void countryIgnoresCase() {
            Gamer candidate = gamer("fi", NOW);
            assertTrue(new FeedFilters(null, "FI", null, null).matches(candidate, clock));
        }

        @Test
        @DisplayName("online means active inside the window, not connected this instant")
        void onlineUsesTheWindow() {
            assertTrue(new FeedFilters(null, null, true, null).matches(gamer("FI", NOW.minusSeconds(600)), clock));
            assertFalse(new FeedFilters(null, null, true, null).matches(gamer("FI", NOW.minusSeconds(3600)), clock));
        }

        @Test
        @DisplayName("somebody who has never been seen is not online")
        void neverActiveIsNotOnline() {
            assertFalse(new FeedFilters(null, null, true, null).matches(gamer("FI", null), clock));
        }

        @Test
        @DisplayName("every filter set has to pass, not any of them")
        void filtersCombine() {
            Gamer candidate = gamer("FI", NOW, "game-1");
            assertTrue(new FeedFilters("game-1", "FI", true, null).matches(candidate, clock));
            // Right game, right country, but stale.
            assertFalse(new FeedFilters("game-1", "FI", true, null)
                    .matches(gamer("FI", NOW.minusSeconds(9999), "game-1"), clock));
            // Right game, wrong country.
            assertFalse(new FeedFilters("game-1", "SE", true, null).matches(candidate, clock));
        }

        @Test
        @DisplayName("an unset filter never excludes anybody")
        void unsetFieldsAreIgnored() {
            assertTrue(FeedFilters.none().matches(gamer(null, null), clock));
        }
    }

    @Nested
    @DisplayName("platform")
    class PlatformFilter {

        @Test
        @DisplayName("matching one of several is enough")
        void anyOverlapPasses() {
            // The whole reason platforms are a set: somebody on PC and Switch is a
            // legitimate answer to both searches, and an equality test would fail both.
            Gamer both = on(Platform.PC, Platform.SWITCH);

            assertTrue(new FeedFilters(null, null, null, Platform.PC).matches(both, clock));
            assertTrue(new FeedFilters(null, null, null, Platform.SWITCH).matches(both, clock));
        }

        @Test
        @DisplayName("somebody on another platform is excluded")
        void noOverlapFails() {
            assertFalse(
                    new FeedFilters(null, null, null, Platform.XBOX).matches(on(Platform.PC, Platform.SWITCH), clock));
        }

        @Test
        @DisplayName("somebody who never said is not excluded")
        void silenceIsNotANo() {
            // Every account created before the field existed has an empty set. Reading that
            // as "not on your platform" would hide people who may well be, and would punish
            // them for the timing of their signup rather than for anything they chose.
            assertTrue(new FeedFilters(null, null, null, Platform.XBOX).matches(on(), clock));
        }

        @Test
        @DisplayName("it combines with the others rather than replacing them")
        void combinesWithOtherFilters() {
            Gamer candidate = gamer("FI", NOW, "game-1");
            candidate.setPlatforms(new LinkedHashSet<>(Set.of(Platform.PC)));

            assertTrue(new FeedFilters("game-1", "FI", true, Platform.PC).matches(candidate, clock));
            // Everything else right, wrong platform.
            assertFalse(new FeedFilters("game-1", "FI", true, Platform.XBOX).matches(candidate, clock));
        }
    }
}
