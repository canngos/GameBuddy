package com.gamebuddy.shared.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.common.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The moderator must not be part of the population.
 *
 * <p>Tested on the entity rather than through each screen because that is where the rule
 * lives — every list a gamer can appear in funnels through {@link Gamer#isPairableWith},
 * and the three native queries that cannot call it repeat the predicate in SQL.
 */
class GamerDiscoverabilityTest {

    private static Gamer gamer(Role role, int age) {
        Gamer g = new Gamer();
        g.setUserId(role.name() + "-" + age);
        g.setAge(age);
        g.setRole(role);
        return g;
    }

    @Test
    void anOrdinaryAccountIsDiscoverable() {
        assertTrue(gamer(Role.USER, 24).isDiscoverable());
    }

    @Test
    void theModeratorIsNot() {
        assertFalse(gamer(Role.ADMIN, 24).isDiscoverable());
    }

    @Test
    @DisplayName("the moderator is never pairable, whatever else lines up")
    void theModeratorIsNeverPairable() {
        Gamer user = gamer(Role.USER, 24);
        Gamer moderator = gamer(Role.ADMIN, 24);

        assertFalse(user.isPairableWith(moderator), "same age band, no blocks, not banned — and still not shown");
        assertTrue(user.isPairableWith(gamer(Role.USER, 25)), "an ordinary account in the same band still is");
    }

    @Test
    @DisplayName("a new Gamer defaults to USER, so nothing accidentally vanishes from the deck")
    void defaultsToDiscoverable() {
        assertTrue(new Gamer().isDiscoverable());
    }
}
