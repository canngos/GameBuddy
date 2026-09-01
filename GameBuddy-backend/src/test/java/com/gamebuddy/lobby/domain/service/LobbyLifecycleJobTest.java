package com.gamebuddy.lobby.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.lobby.infrastructure.entity.Lobby;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMember;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMemberRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyMessageRepository;
import com.gamebuddy.lobby.infrastructure.repository.LobbyRepository;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LobbyLifecycleJobTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    @InjectMocks
    private LobbyLifecycleJob job;

    @Mock
    private LobbyRepository lobbyRepository;

    @Mock
    private LobbyMemberRepository memberRepository;

    @Mock
    private LobbyMessageRepository messageRepository;

    @Mock
    private GamerRepository gamerRepository;

    @Spy
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        when(lobbyRepository.findAllByStatusAndStartsAtBefore(any(), any())).thenReturn(List.of());
        when(lobbyRepository.findAllByStatusInAndEndedAtBefore(any(), any())).thenReturn(List.of());
    }

    private static Lobby lobby(LobbyStatus status, Instant startsAt) {
        Lobby lobby = new Lobby();
        lobby.setId(UUID.randomUUID());
        lobby.setOwnerId("owner-1");
        lobby.setGameId("game-1");
        lobby.setTitle("ranked grind");
        lobby.setTone(LobbyTone.COMPETITIVE);
        lobby.setMaxPlayers(3);
        lobby.setStartsAt(startsAt);
        lobby.setStatus(status);
        return lobby;
    }

    @Test
    @DisplayName("an OPEN lobby a day past its planned start is cancelled and the accepted are told")
    void testSweep_cancelsAbandonedAndNotifiesAccepted() {
        Lobby abandoned = lobby(LobbyStatus.OPEN, NOW.minus(Duration.ofHours(25)));
        when(lobbyRepository.findAllByStatusAndStartsAtBefore(
                        eq(LobbyStatus.OPEN), eq(NOW.minus(LobbyLifecycleJob.CANCEL_UNSTARTED_AFTER))))
                .thenReturn(List.of(abandoned));

        Gamer accepted = new Gamer();
        accepted.setUserId("member-1");
        accepted.setEmail("member@example.com");
        when(memberRepository.findAllByLobbyIdAndStatus(abandoned.getId(), LobbyMemberStatus.ACCEPTED))
                .thenReturn(List.of(new LobbyMember(abandoned.getId(), "member-1", LobbyMemberStatus.ACCEPTED, NOW)));
        when(gamerRepository.findById("member-1")).thenReturn(Optional.of(accepted));

        job.sweep();

        assertEquals(LobbyStatus.CANCELLED, abandoned.getStatus());
        assertEquals(NOW, abandoned.getEndedAt());
        ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(NotificationKind.LOBBY_CANCELLED, event.getValue().kind());
    }

    @Test
    @DisplayName("a LOCKED lobby two days past its planned start is quietly marked ENDED")
    void testSweep_endsForgottenLockedLobbies() {
        Lobby forgotten = lobby(LobbyStatus.LOCKED, NOW.minus(Duration.ofHours(49)));
        when(lobbyRepository.findAllByStatusAndStartsAtBefore(
                        eq(LobbyStatus.LOCKED), eq(NOW.minus(LobbyLifecycleJob.END_FORGOTTEN_AFTER))))
                .thenReturn(List.of(forgotten));

        job.sweep();

        assertEquals(LobbyStatus.ENDED, forgotten.getStatus());
        assertEquals(NOW, forgotten.getEndedAt());
        // Quietly: nothing anybody cares about changed.
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("a month after ending, the lobby archives and its chat is deleted in one statement")
    void testSweep_archivesOldAndDeletesMessages() {
        Lobby old = lobby(LobbyStatus.ENDED, NOW.minus(Duration.ofDays(40)));
        old.setEndedAt(NOW.minus(Duration.ofDays(31)));
        when(lobbyRepository.findAllByStatusInAndEndedAtBefore(any(), eq(NOW.minus(LobbyLifecycleJob.ARCHIVE_AFTER))))
                .thenReturn(List.of(old));

        job.sweep();

        assertEquals(LobbyStatus.ARCHIVED, old.getStatus());
        verify(messageRepository).deleteAllByLobbyIdIn(List.of(old.getId()));
    }

    @Test
    @DisplayName("a quiet sweep touches nothing — idempotence is selecting only what is due")
    void testSweep_whenNothingDue_DoesNothing() {
        job.sweep();

        verify(lobbyRepository, never()).save(any());
        verify(messageRepository, never()).deleteAllByLobbyIdIn(any());
        verify(events, never()).publishEvent(any());
    }
}
