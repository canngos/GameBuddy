package com.gamebuddy.match.domain.service;

import static org.junit.jupiter.api.Assertions.*;

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
            assertFalse(new FeedFilters(null, null, false).narrowing());
        }

        @Test
        void anySetFieldNarrows() {
            assertTrue(new FeedFilters("game-1", null, null).narrowing());
            assertTrue(new FeedFilters(null, "FI", null).narrowing());
            assertTrue(new FeedFilters(null, null, true).narrowing());
        }
    }

    @Nested
    @DisplayName("activeSince")
    class ActiveSince {

        @Test
        @DisplayName("null unless the online filter is actually on")
        void nullWhenNotFiltering() {
            assertNull(FeedFilters.none().activeSince(clock));
            assertNull(new FeedFilters(null, null, false).activeSince(clock));
        }

        @Test
        @DisplayName("the cutoff the database is given is the same one matches() uses")
        void agreesWithMatches() {
            // These two run in different places — one in SQL to decide who gets ranked,
            // one in memory to decide who gets shown — and a disagreement between them
            // would show offline people under an "online now" filter.
            FeedFilters filters = new FeedFilters(null, null, true);
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
            assertTrue(new FeedFilters("game-2", null, null).matches(candidate, clock));
            assertFalse(new FeedFilters("game-9", null, null).matches(candidate, clock));
        }

        @Test
        @DisplayName("country is compared case-insensitively")
        void countryIgnoresCase() {
            Gamer candidate = gamer("fi", NOW);
            assertTrue(new FeedFilters(null, "FI", null).matches(candidate, clock));
        }

        @Test
        @DisplayName("online means active inside the window, not connected this instant")
        void onlineUsesTheWindow() {
            assertTrue(new FeedFilters(null, null, true).matches(gamer("FI", NOW.minusSeconds(600)), clock));
            assertFalse(new FeedFilters(null, null, true).matches(gamer("FI", NOW.minusSeconds(3600)), clock));
        }

        @Test
        @DisplayName("somebody who has never been seen is not online")
        void neverActiveIsNotOnline() {
            assertFalse(new FeedFilters(null, null, true).matches(gamer("FI", null), clock));
        }

        @Test
        @DisplayName("every filter set has to pass, not any of them")
        void filtersCombine() {
            Gamer candidate = gamer("FI", NOW, "game-1");
            assertTrue(new FeedFilters("game-1", "FI", true).matches(candidate, clock));
            // Right game, right country, but stale.
            assertFalse(new FeedFilters("game-1", "FI", true)
                    .matches(gamer("FI", NOW.minusSeconds(9999), "game-1"), clock));
            // Right game, wrong country.
            assertFalse(new FeedFilters("game-1", "SE", true).matches(candidate, clock));
        }

        @Test
        @DisplayName("an unset filter never excludes anybody")
        void unsetFieldsAreIgnored() {
            assertTrue(FeedFilters.none().matches(gamer(null, null), clock));
        }
    }
}
