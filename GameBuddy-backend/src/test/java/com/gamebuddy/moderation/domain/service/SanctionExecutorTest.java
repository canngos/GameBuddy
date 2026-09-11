package com.gamebuddy.moderation.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport.ReasonCode;
import com.gamebuddy.moderation.infrastructure.entity.ModerationAction;
import com.gamebuddy.moderation.infrastructure.entity.ModerationAction.Action;
import com.gamebuddy.moderation.infrastructure.repository.ModerationActionRepository;
import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SanctionExecutor")
class SanctionExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private ModerationActionRepository actions;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private SanctionExecutor executor;

    private Gamer target;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        executor = new SanctionExecutor(gamerRepository, actions, events, Clock.fixed(NOW, ZoneOffset.UTC));
        target = new Gamer();
        target.setUserId("target-1");
        target.setFcmToken("dev-token");
    }

    @Test
    @DisplayName("a 24h suspension blocks with an end time and revokes tokens, and says when it ends")
    void suspensionBlocksWithAnEndTime() {
        ModerationAction row = executor.apply(
                target, UUID.randomUUID(), "mod-1", Action.SUSPEND_24H, ReasonCode.HARASSMENT, null, false);

        assertTrue(Boolean.TRUE.equals(target.getIsBlocked()));
        assertEquals(NOW.plusSeconds(24 * 3600), target.getSuspendedUntil());
        assertEquals(NOW.plusSeconds(24 * 3600), row.getExpiresAt());
        verify(gamerRepository).save(target);
        verify(actions).save(any(ModerationAction.class));

        ArgumentCaptor<NotificationRequestedEvent> captor = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertEquals(NotificationKind.SANCTION, captor.getValue().kind());
        assertTrue(
                captor.getValue().body().contains(SanctionExecutor.APPEAL_ADDRESS), "the notice names where to appeal");
    }

    @Test
    @DisplayName("a ban is permanent — blocked with no end time — and needs a note")
    void banIsPermanentAndNeedsANote() {
        assertThrows(
                BusinessException.class,
                () -> executor.apply(target, null, "mod-1", Action.BAN, ReasonCode.SEXUAL, "  ", false));

        ModerationAction row =
                executor.apply(target, null, "mod-1", Action.BAN, ReasonCode.SEXUAL, "explicit avatar", false);
        assertTrue(Boolean.TRUE.equals(target.getIsBlocked()));
        assertNull(target.getSuspendedUntil(), "no end time is what makes it permanent");
        assertNull(row.getExpiresAt());
    }

    @Test
    @DisplayName("removing the photo hides it as REJECTED without deleting the object")
    void removePhotoRejects() {
        target.setAvatarStatus(AvatarStatus.APPROVED);
        target.setAvatarKey("obj-key");

        executor.apply(target, UUID.randomUUID(), "mod-1", Action.REMOVE_PHOTO, ReasonCode.SEXUAL, null, false);

        assertEquals(AvatarStatus.REJECTED, target.getAvatarStatus());
        assertEquals("obj-key", target.getAvatarKey(), "kept for an appeal, like the review path");
    }

    @Test
    @DisplayName("a dismissal tells nobody and blocks nothing")
    void dismissIsSilent() {
        executor.apply(target, UUID.randomUUID(), "mod-1", Action.DISMISS, null, null, false);

        assertFalse(Boolean.TRUE.equals(target.getIsBlocked()));
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("a warning notifies but does not block")
    void warningNotifiesWithoutBlocking() {
        executor.apply(target, UUID.randomUUID(), "mod-1", Action.WARN, ReasonCode.SPAM_SCAM, null, false);

        assertFalse(Boolean.TRUE.equals(target.getIsBlocked()));
        verify(events).publishEvent(any(NotificationRequestedEvent.class));
    }
}
