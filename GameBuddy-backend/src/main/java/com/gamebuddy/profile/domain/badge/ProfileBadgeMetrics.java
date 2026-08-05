package com.gamebuddy.profile.domain.badge;

import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What the profile module can count: everything that is on the gamer already.
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

    @Override
    public Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> counts = new EnumMap<>(BadgeMetric.class);
        counts.put(BadgeMetric.MATCHES, mutualMatches(gamer));
        counts.put(BadgeMetric.FRIENDS, gamer.getFriends().size());
        // Only bought ones have a row, which is what makes this an achievement: the free
        // frames everybody owns cannot carry a gamer towards "Rich in the Hood".
        counts.put(
                BadgeMetric.COSMETICS_OWNED,
                ownershipRepository.findOwnedIds(gamer.getUserId()).size());
        counts.put(BadgeMetric.COSMETICS_WORN, worn(gamer));
        counts.put(BadgeMetric.PROFILE_COMPLETENESS, completeness(gamer));
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
