package com.gamebuddy.profile.domain.badge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rules about the catalogue that nothing else can enforce.
 *
 * <p>A badge is a declaration, so most mistakes in one are invisible until somebody earns
 * it and gets the wrong thing. These are the invariants that used to be true only because
 * whoever last edited the enum remembered them — and the reward drift they did not catch is
 * exactly why the tier now decides the payout.
 */
@DisplayName("the badge catalogue")
class BadgeCatalogueTest {

    /**
     * Codes retired with the Community feature.
     *
     * <p>Spent forever. Their rows survive on live accounts, so reusing one would resurrect
     * everybody's old badge under a new name — somebody who wrote a forum post in 2026
     * would wake up holding whatever the code was recycled into.
     */
    private static final Set<String> BURNED = Set.of("guild-member", "say-something", "local-legend");

    @Test
    @DisplayName("every code is unique, kebab-case, and not one of the retired ones")
    void codesAreUsable() {
        Set<String> seen = new HashSet<>();
        for (Badge badge : Badge.values()) {
            String code = badge.getCode();
            assertTrue(seen.add(code), code + " appears twice");
            assertFalse(BURNED.contains(code), code + " was retired and its rows still exist on live accounts");
            assertTrue(code.matches("[a-z0-9]+(-[a-z0-9]+)*"), code + " is not kebab-case, and it is a filename");
        }
    }

    @Test
    @DisplayName("the tier decides the reward, except where a cosmetic replaces it")
    void rewardsFollowTheTier() {
        for (Badge badge : Badge.values()) {
            if (badge.grantsCosmetic()) {
                continue;
            }
            assertEquals(
                    badge.getTier().getReward(),
                    badge.getReward(),
                    badge + " pays " + badge.getReward() + " but its tier pays "
                            + badge.getTier().getReward()
                            + " — hand-picked figures are what let the catalogue drift to 775 coins last time");
        }
    }

    @Test
    @DisplayName("nothing pays more than the hard tier")
    void nothingOutpaysPrismatic() {
        int cap = BadgeTier.PRISMATIC.getReward();
        assertEquals(125, cap);
        for (Badge badge : Badge.values()) {
            assertTrue(badge.getReward() <= cap, badge + " pays more than the hardest badge in the game");
        }
    }

    @Test
    @DisplayName("a badge pays coins or a cosmetic, never both and never neither")
    void exactlyOneReward() {
        for (Badge badge : Badge.values()) {
            if (badge.grantsCosmetic()) {
                assertEquals(0, badge.getReward(), badge + " grants a cosmetic and also pays coins");
                assertEquals(BadgeTier.PRISMATIC, badge.getTier(), "only the hard tier hands over cosmetics");
            } else {
                assertTrue(badge.getReward() > 0, badge + " pays nothing at all");
            }
        }
    }

    @Test
    @DisplayName("only the hard tier is animated, and only two of it")
    void animationIsRare() {
        List<Badge> animated =
                Arrays.stream(Badge.values()).filter(Badge::isAnimated).toList();

        assertEquals(2, animated.size(), "animated WebP is the most expensive thing the app decodes");
        assertTrue(animated.stream().allMatch(b -> b.getTier() == BadgeTier.PRISMATIC));
        assertTrue(
                animated.stream().allMatch(b -> b.iconKey().endsWith(".webp")),
                "an animated badge must ask for the animated file");
        assertTrue(
                Arrays.stream(Badge.values()).filter(b -> !b.isAnimated()).allMatch(b -> b.iconKey()
                        .endsWith(".png")),
                "a still badge must not ask for a file that was never generated");
    }

    @Test
    @DisplayName("earning every other badge means every other badge")
    void completionistTargetTracksTheCatalogue() {
        assertEquals(
                Badge.values().length - 1,
                Badge.COMPLETIONIST.getTarget(),
                "adding a badge without moving this makes COMPLETIONIST reachable one badge early");
    }

    @Test
    @DisplayName("the catalogue is worth what the tiers say it is")
    void lifetimeCoinTotal() {
        int total = Arrays.stream(Badge.values()).mapToInt(Badge::getReward).sum();

        // 7 bronze + 8 silver + 3 gold + 3 prismatic that pay coins. The four prismatic
        // ones that hand over a frame contribute nothing here, which is the point of them.
        //
        // Up from 775, but across two and a half times as many badges and with the biggest
        // single payout down from 200 to 125 — which is the shape the owner asked for: more
        // to chase, less handed over for each, and the longest ones paying in things that
        // cost the economy nothing at all.
        assertEquals(1175, total);
        assertTrue(
                Arrays.stream(Badge.values()).filter(Badge::grantsCosmetic).count() == 4,
                "four hard badges pay in frames rather than coins");
    }
}
