package com.gamebuddy.lobby.domain.service;

import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMemberRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMessageRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyRepository;
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
 *
 * <p>Four counts on a path that runs on every profile load. They are all indexed
 * single-column counts and they are all this module's, so they cost one round trip
 * between them rather than one each — but this is the class to look at first if the
 * badges screen ever gets slow.
 */
@Component
@RequiredArgsConstructor
public class LobbyBadgeMetrics implements BadgeMetricSource {

    /**
     * Every row this gamer has ever had against a lobby they actually got into.
     *
     * <p>PENDING and REJECTED are absent because neither ever put anybody in a team, and
     * LEFT and KICKED are present because both did — the difference between this and
     * {@link LobbyMemberStatus#inTeam()} is history against standing, which is exactly the
     * difference between a mission and a badge.
     */
    private static final EnumSet<LobbyMemberStatus> EVER_IN_TEAM = EnumSet.of(
            LobbyMemberStatus.OWNER, LobbyMemberStatus.ACCEPTED, LobbyMemberStatus.LEFT, LobbyMemberStatus.KICKED);

    private final LobbyMemberRepository memberRepository;
    private final LobbyRepository lobbyRepository;
    private final LobbyMessageRepository messageRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> counts = new EnumMap<>(BadgeMetric.class);
        // Teams this gamer has been part of: lobbies they opened and lobbies they were
        // accepted into. A pending or rejected request achieved nothing yet; a LEFT or
        // KICKED row no longer counts — the metric is standing, not history.
        counts.put(BadgeMetric.LOBBIES_JOINED, (int) memberRepository.countByUserIdAndStatusIn(
                gamer.getUserId(), EnumSet.of(LobbyMemberStatus.OWNER, LobbyMemberStatus.ACCEPTED)));
        // The same question asked of history rather than of now, because a mission that
        // asks for three lobbies cannot be undone by leaving one.
        counts.put(BadgeMetric.LOBBIES_JOINED_EVER, (int)
                memberRepository.countByUserIdAndStatusIn(gamer.getUserId(), EVER_IN_TEAM));
        counts.put(BadgeMetric.LOBBIES_CREATED, (int) lobbyRepository.countByOwnerId(gamer.getUserId()));
        counts.put(BadgeMetric.LOBBY_MESSAGES_SENT, (int) messageRepository.countBySenderId(gamer.getUserId()));
        return counts;
    }
}
