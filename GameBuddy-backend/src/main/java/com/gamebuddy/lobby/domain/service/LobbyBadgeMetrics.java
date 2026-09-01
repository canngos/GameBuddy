package com.gamebuddy.lobby.domain.service;

import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMemberRepository;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.entity.Gamer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the lobby module contributes to badges and quests.
 *
 * <p>Same inversion as every other {@code BadgeMetricSource}: these are this module's
 * tables, so the counting happens here and the evaluator never learns who produced the
 * number.
 */
@Component
@RequiredArgsConstructor
public class LobbyBadgeMetrics implements BadgeMetricSource {

    private final LobbyMemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> counts = new EnumMap<>(BadgeMetric.class);
        // Teams this gamer has been part of: lobbies they opened and lobbies they were
        // accepted into. A pending or rejected request achieved nothing yet; a LEFT or
        // KICKED row no longer counts — the metric is standing, not history.
        counts.put(BadgeMetric.LOBBIES_JOINED, (int) memberRepository.countByUserIdAndStatusIn(
                gamer.getUserId(), EnumSet.of(LobbyMemberStatus.OWNER, LobbyMemberStatus.ACCEPTED)));
        return counts;
    }
}
