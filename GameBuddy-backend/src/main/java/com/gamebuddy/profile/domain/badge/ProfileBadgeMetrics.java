package com.gamebuddy.profile.domain.badge;

import com.gamebuddy.profile.infrastructure.repository.RewardedAdGrantRepository;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.coin.CoinLedgerRepository;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerBadgeRepository;
import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the profile module can count: everything that is on the gamer already, plus the
 * three ledgers this module owns.
 *
 * <p>Matches and friends are {@code @ManyToMany} collections on {@link Gamer} rather than
 * tables belonging to the match module, so they are read straight off the entity. That is
 * three collection loads on a screen that is opened by hand, which is the right trade
 * against a second set of count queries.
 */
@Component
@RequiredArgsConstructor
public class ProfileBadgeMetrics implements BadgeMetricSource {

    /** Games and keywords each count towards a complete profile at this many picked. */
    private static final int ENOUGH_PICKED = 3;

    private final GamerCosmeticRepository ownershipRepository;
    private final GamerBadgeRepository badgeRepository;
    private final RewardedAdGrantRepository adGrantRepository;
    private final CoinLedgerRepository ledgerRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> counts = new EnumMap<>(BadgeMetric.class);
        counts.put(BadgeMetric.MATCHES, mutualMatches(gamer));
        // Everyone this gamer swiped yes at, answered or not. The collection is already
        // loaded for the line above, so this is free where a `count(*)` would not be — and
        // unlike MATCHES it only ever grows, which is what a mission needs.
        counts.put(BadgeMetric.LIKES_SENT, gamer.getApprovedMatches().size());
        counts.put(BadgeMetric.FRIENDS, gamer.getFriends().size());
        // Only bought ones have a row, which is what makes this an achievement: the free
        // frames everybody owns cannot carry a gamer towards "Rich in the Hood".
        counts.put(
                BadgeMetric.COSMETICS_OWNED,
                ownershipRepository.findOwnedIds(gamer.getUserId()).size());
        counts.put(BadgeMetric.COSMETICS_WORN, worn(gamer));
        counts.put(BadgeMetric.PROFILE_COMPLETENESS, completeness(gamer));

        // Turning up. The streak is what it is right now; the total remembers every day.
        counts.put(BadgeMetric.DAILY_STREAK, gamer.getDailyStreak());
        counts.put(BadgeMetric.DAILY_CLAIMS, gamer.getDailyClaimsTotal());

        counts.put(BadgeMetric.ADS_WATCHED, (int) adGrantRepository.countByUserId(gamer.getUserId()));
        counts.put(BadgeMetric.COINS_SPENT, (int) ledgerRepository.sumSpent(gamer.getUserId()));
        counts.put(BadgeMetric.BADGES_EARNED, (int) badgeRepository.countByUserId(gamer.getUserId()));
        // The cursor points at the set being played, so the number *finished* is one less.
        // Clamped because a gamer who has never opened the earn screen sits at zero.
        counts.put(BadgeMetric.MISSION_SETS_DONE, Math.max(0, gamer.getMissionSetIndex() - 1));
        return counts;
    }

    /**
     * Matches where the other side accepted back.
     *
     * <p>Same rule the match module uses to decide who may be chatted with: swiping yes at
     * ten people who never answered is not ten matches.
     */
    private int mutualMatches(Gamer gamer) {
        return (int) gamer.getApprovedMatches().stream()
                .filter(other -> other.getApprovedMatches().contains(gamer))
                .count();
    }

    private int worn(Gamer gamer) {
        return (gamer.getEquippedFrame() == null ? 0 : 1) + (gamer.getEquippedBanner() == null ? 0 : 1);
    }

    private int completeness(Gamer gamer) {
        int done = 0;
        // An uploaded picture or a chosen default both count. The mission is "look like
        // somebody", not "give us a photograph".
        if (gamer.getAvatarKey() != null || gamer.getAvatar() != null) {
            done++;
        }
        if (gamer.getLikedgames().size() >= ENOUGH_PICKED) {
            done++;
        }
        if (gamer.getKeywords().size() >= ENOUGH_PICKED) {
            done++;
        }
        return done;
    }
}
