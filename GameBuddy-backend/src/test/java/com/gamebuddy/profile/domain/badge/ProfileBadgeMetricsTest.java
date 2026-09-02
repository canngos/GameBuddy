package com.gamebuddy.profile.domain.badge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.profile.infrastructure.repository.RewardedAdGrantRepository;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.coin.CoinLedgerRepository;
import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.Games;
import com.gamebuddy.shared.entity.Keywords;
import com.gamebuddy.shared.repository.GamerBadgeRepository;
import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileBadgeMetricsTest {

    @InjectMocks
    private ProfileBadgeMetrics metrics;

    @Mock
    private GamerCosmeticRepository ownershipRepository;

    @Mock
    private GamerBadgeRepository badgeRepository;

    @Mock
    private RewardedAdGrantRepository adGrantRepository;

    @Mock
    private CoinLedgerRepository ledgerRepository;

    private Gamer gamer;

    @BeforeEach
    void setUp() {
        gamer = newGamer("me");
        when(ownershipRepository.findOwnedIds(anyString())).thenReturn(Set.of());
        // The three counting repositories default to a fresh account. Every test that
        // cares about one of them says so; the rest would otherwise NPE on a metric they
        // are not about.
        when(badgeRepository.countByUserId(anyString())).thenReturn(0L);
        when(adGrantRepository.countByUserId(anyString())).thenReturn(0L);
        when(ledgerRepository.sumSpent(anyString())).thenReturn(0L);
    }

    private static Gamer newGamer(String name) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setGamerUsername(name);
        return g;
    }

    private Map<BadgeMetric, Integer> measure() {
        return metrics.measure(gamer);
    }

    @Test
    @DisplayName("a match counts only when the other side accepted back")
    void testMeasure_whenSwipesAreNotReciprocated_DoesNotCountThem() {
        // Swiping yes at five people who never answered is not five matches, and the
        // mission says "match with", not "like".
        for (int i = 0; i < 5; i++) {
            gamer.getApprovedMatches().add(newGamer("g" + i));
        }
        Gamer mutual = newGamer("mutual");
        mutual.getApprovedMatches().add(gamer);
        gamer.getApprovedMatches().add(mutual);

        assertEquals(1, measure().get(BadgeMetric.MATCHES));
    }

    @Test
    void testMeasure_whenFriends_CountsThem() {
        gamer.getFriends().add(newGamer("a"));
        gamer.getFriends().add(newGamer("b"));

        assertEquals(2, measure().get(BadgeMetric.FRIENDS));
    }

    @Test
    @DisplayName("free cosmetics do not count towards owning three")
    void testMeasure_whenCosmeticsAreFree_DoesNotCountThem() {
        // Free items deliberately have no purchase row — see GamerCosmetic — so counting
        // rows is exactly the rule "how many did you actually buy".
        when(ownershipRepository.findOwnedIds(gamer.getUserId())).thenReturn(Set.of(UUID.randomUUID()));

        assertEquals(1, measure().get(BadgeMetric.COSMETICS_OWNED));
    }

    @Test
    void testMeasure_whenWearingBoth_CountsTwo() {
        gamer.setEquippedFrame(new Cosmetic());
        gamer.setEquippedBanner(new Cosmetic());

        assertEquals(2, measure().get(BadgeMetric.COSMETICS_WORN));
    }

    @Test
    void testMeasure_whenWearingNothing_CountsZero() {
        assertEquals(0, measure().get(BadgeMetric.COSMETICS_WORN));
    }

    @Test
    @DisplayName("a complete profile is a picture, three games and three keywords")
    void testMeasure_whenProfileComplete_CountsThree() {
        gamer.setAvatarKey("avatars/x/a.jpg");
        for (int i = 0; i < 3; i++) {
            gamer.getLikedgames().add(new Games());
            gamer.getKeywords().add(new Keywords());
        }

        assertEquals(3, measure().get(BadgeMetric.PROFILE_COMPLETENESS));
    }

    @Test
    @DisplayName("a chosen stock avatar counts as much as an upload")
    void testMeasure_whenDefaultAvatarChosen_CountsThePicture() {
        // The mission is "look like somebody", not "give us a photograph".
        gamer.setAvatar(UUID.randomUUID());

        assertEquals(1, measure().get(BadgeMetric.PROFILE_COMPLETENESS));
    }

    @Test
    @DisplayName("two of three games is not a third of the way there")
    void testMeasure_whenPartlyPicked_DoesNotCountThatSlice() {
        for (int i = 0; i < 2; i++) {
            gamer.getLikedgames().add(new Games());
        }

        assertEquals(0, measure().get(BadgeMetric.PROFILE_COMPLETENESS));
    }

    // --- the metrics the mission campaign added -----------------------------

    @Test
    @DisplayName("likes sent counts everyone swiped at, where matches counts only the mutual ones")
    void likesAreNotMatches() {
        Gamer other = newGamer("other");
        // One-sided: this gamer said yes, the other never answered.
        gamer.getApprovedMatches().add(other);

        Map<BadgeMetric, Integer> out = measure();

        assertEquals(1, out.get(BadgeMetric.LIKES_SENT));
        assertEquals(0, out.get(BadgeMetric.MATCHES));
    }

    @Test
    @DisplayName("the streak is what it is now; the claim total remembers every day")
    void streakAndTotalAreDifferentQuestions() {
        gamer.setDailyStreak(3);
        gamer.setDailyClaimsTotal(58);

        Map<BadgeMetric, Integer> out = measure();

        assertEquals(3, out.get(BadgeMetric.DAILY_STREAK));
        assertEquals(58, out.get(BadgeMetric.DAILY_CLAIMS));
    }

    @Test
    @DisplayName("coins spent comes off the ledger, not off the balance")
    void spendIsLedgerNotBalance() {
        // A gamer who earned 5,000 and spent 5,000 has nothing left and has spent a lot.
        gamer.setCoin(0);
        when(ledgerRepository.sumSpent(gamer.getUserId())).thenReturn(5000L);

        assertEquals(5000, measure().get(BadgeMetric.COINS_SPENT));
    }

    @Test
    @DisplayName("the campaign badge counts sets finished, not the set in play")
    void missionSetsDoneExcludesTheCurrentOne() {
        // Dealt set 1 and playing it: nothing is finished yet.
        gamer.setMissionSetIndex(1);
        assertEquals(0, measure().get(BadgeMetric.MISSION_SETS_DONE));

        // Set 9 is only ever dealt once set 8 has been claimed out, so eight are done.
        gamer.setMissionSetIndex(9);
        assertEquals(8, measure().get(BadgeMetric.MISSION_SETS_DONE));
    }

    @Test
    @DisplayName("a gamer who has never opened the earn screen is at zero, not at minus one")
    void missionSetsDoneIsClamped() {
        gamer.setMissionSetIndex(0);
        assertEquals(0, measure().get(BadgeMetric.MISSION_SETS_DONE));
    }
}
