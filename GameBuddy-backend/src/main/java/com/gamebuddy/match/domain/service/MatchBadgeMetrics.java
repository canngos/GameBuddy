package com.gamebuddy.match.domain.service;

import com.gamebuddy.match.infrastructure.repository.ChatMessageRepository;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.entity.Gamer;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the match module contributes to badges: how much a gamer has said.
 *
 * <p>Matches themselves are not here. They are a {@code @ManyToMany} on {@link Gamer}
 * rather than a table this module owns, so the profile module reads them off the entity
 * directly — see {@code ProfileBadgeMetrics}. Chat messages are this module's, and are
 * encrypted at rest, so counting them is the only thing anyone outside can do with them.
 */
@Component
@RequiredArgsConstructor
public class MatchBadgeMetrics implements BadgeMetricSource {

    private final ChatMessageRepository chatMessageRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> counts = new EnumMap<>(BadgeMetric.class);
        counts.put(BadgeMetric.MESSAGES_SENT, (int) chatMessageRepository.countBySenderId(gamer.getUserId()));
        return counts;
    }
}
