package com.gamebuddy.lobby.domain.service;

import com.gamebuddy.lobby.infrastructure.entity.Lobby;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMemberRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMessageRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyRepository;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sweeper: files away what owners visibly abandoned, and nothing else.
 *
 * <p>Every threshold here is generous by design, because the owner's word outranks the
 * clock everywhere in this feature. Locking starts no timer at all; the only timers that
 * exist are these, and they run from the <em>planned start</em>, days behind it:
 *
 * <ul>
 *   <li>OPEN a full day past its planned start — nobody came; cancelled, members told.
 *   <li>LOCKED two days past it — played and never closed; marked ENDED, chat kept
 *       readable, nobody notified because nothing anybody cares about changed.
 *   <li>ENDED or CANCELLED for thirty days — archived: gone from the app, chat deleted.
 *       The lobby and member rows stay for whatever stats want them later.
 * </ul>
 *
 * <p>Idempotent by construction — every query selects exactly the rows still in the state
 * being swept, so a second run over the same data finds nothing to do. Scheduling is
 * enabled by {@code AsyncConfig}; the pattern is {@code MembershipCosmeticsJob}'s.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LobbyLifecycleJob {

    static final Duration CANCEL_UNSTARTED_AFTER = Duration.ofHours(24);
    static final Duration END_FORGOTTEN_AFTER = Duration.ofHours(48);
    static final Duration ARCHIVE_AFTER = Duration.ofDays(30);

    private final LobbyRepository lobbyRepository;
    private final LobbyMemberRepository memberRepository;
    private final LobbyMessageRepository messageRepository;
    private final GamerRepository gamerRepository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void sweep() {
        Instant now = clock.instant();
        cancelAbandoned(now);
        endForgotten(now);
        archiveOld(now);
    }

    /** Still OPEN a day past the planned start: the team never formed. */
    private void cancelAbandoned(Instant now) {
        List<Lobby> abandoned = lobbyRepository.findAllByStatusAndStartsAtBefore(
                LobbyStatus.OPEN, now.minus(CANCEL_UNSTARTED_AFTER));
        for (Lobby lobby : abandoned) {
            lobby.setStatus(LobbyStatus.CANCELLED);
            lobby.setEndedAt(now);
            lobbyRepository.save(lobby);
            // The few who were accepted were counting on it; they get the same push an
            // owner's cancel sends. Pending requesters are left alone, as everywhere.
            memberRepository
                    .findAllByLobbyIdAndStatus(lobby.getId(), LobbyMemberStatus.ACCEPTED)
                    .forEach(member -> gamerRepository
                            .findById(member.getUserId())
                            .ifPresent(gamer -> events.publishEvent(new NotificationRequestedEvent(
                                    gamer.getUserId(),
                                    gamer.getFcmToken(),
                                    lobby.getTitle(),
                                    "This lobby was called off",
                                    NotificationKind.LOBBY_CANCELLED,
                                    lobby.getId().toString()))));
        }
        if (!abandoned.isEmpty()) {
            log.info("Cancelled {} lobbies that never started", abandoned.size());
        }
    }

    /** LOCKED two days past the planned start: presumably played, never closed. */
    private void endForgotten(Instant now) {
        List<Lobby> forgotten = lobbyRepository.findAllByStatusAndStartsAtBefore(
                LobbyStatus.LOCKED, now.minus(END_FORGOTTEN_AFTER));
        for (Lobby lobby : forgotten) {
            lobby.setStatus(LobbyStatus.ENDED);
            lobby.setEndedAt(now);
            lobbyRepository.save(lobby);
        }
        if (!forgotten.isEmpty()) {
            log.info("Ended {} lobbies their owners forgot to close", forgotten.size());
        }
    }

    /** Terminal for a month: out of the app, chat deleted, rows kept for stats. */
    private void archiveOld(Instant now) {
        List<Lobby> old = lobbyRepository.findAllByStatusInAndEndedAtBefore(
                EnumSet.of(LobbyStatus.ENDED, LobbyStatus.CANCELLED), now.minus(ARCHIVE_AFTER));
        if (old.isEmpty()) {
            return;
        }
        messageRepository.deleteAllByLobbyIdIn(old.stream().map(Lobby::getId).toList());
        for (Lobby lobby : old) {
            lobby.setStatus(LobbyStatus.ARCHIVED);
            lobbyRepository.save(lobby);
        }
        log.info("Archived {} lobbies and deleted their chats", old.size());
    }
}
