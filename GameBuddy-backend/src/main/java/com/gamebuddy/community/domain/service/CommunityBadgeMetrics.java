package com.gamebuddy.community.domain.service;

import com.gamebuddy.community.infrastructure.repository.CommunityRepository;
import com.gamebuddy.community.infrastructure.repository.PostRepository;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.entity.Gamer;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the community module contributes to badges.
 *
 * <p>The counting is here rather than in the badge evaluator because these are this
 * module's tables. Profile asking {@code PostRepository} for a count directly is exactly
 * the coupling {@code ModuleBoundaryTest} refuses, and the boundary is worth more than the
 * two files it costs to respect it.
 */
@Component
@RequiredArgsConstructor
public class CommunityBadgeMetrics implements BadgeMetricSource {

    private final CommunityRepository communityRepository;
    private final PostRepository postRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> counts = new EnumMap<>(BadgeMetric.class);
        // Owning a community counts as being in it — the owner is also a member, which is
        // how joining is modelled, so this needs no special case.
        counts.put(
                BadgeMetric.COMMUNITIES_JOINED,
                communityRepository.findAllByMembersContaining(gamer).size());
        counts.put(BadgeMetric.POSTS_WRITTEN, (int) postRepository.countByOwner(gamer.getUserId()));
        return counts;
    }
}
