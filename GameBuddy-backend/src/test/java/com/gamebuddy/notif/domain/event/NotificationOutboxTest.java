package com.gamebuddy.notif.domain.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.notif.domain.service.NotificationService;
import com.gamebuddy.notif.infrastructure.entity.NotificationOutbox;
import com.gamebuddy.notif.infrastructure.repository.NotificationOutboxRepository;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/**
 * The outbox: a notification is recorded with the change that caused it, then delivered
 * separately.
 *
 * <p>What these pin is the durability property. The previous shape sent on an after-commit
 * thread, so a process that died between the commit and the send lost the notification with
 * no record it had been owed.
 */
class NotificationOutboxTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);

    /** The dispatcher checks the recipient's preferences before queueing. */
    private final GamerRepository gamers = mock(GamerRepository.class);

    private static NotificationOutbox pending(String token) {
        NotificationOutbox row = new NotificationOutbox();
        row.setId(UUID.randomUUID());
        row.setFcmToken(token);
        row.setTitle("It's a match!");
        row.setBody("You matched with someone");
        row.setCreatedAt(NOW);
        row.setNextAttemptAt(NOW);
        return row;
    }

    @Nested
    class Recording {

        private final NotificationDispatcher dispatcher = new NotificationDispatcher(repository, gamers, CLOCK);

        @Test
        @DisplayName("the notification is written, not sent, when it is requested")
        void writesToTheOutbox() {
            dispatcher.onNotificationRequested(
                    new NotificationRequestedEvent("dev-1", "Title", "Body", NotificationKind.MATCH));

            ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
            verify(repository).save(captor.capture());
            NotificationOutbox row = captor.getValue();

            assertEquals("dev-1", row.getFcmToken());
            assertEquals("Title", row.getTitle());
            assertNull(row.getSentAt(), "not delivered yet");
            assertEquals(NOW, row.getNextAttemptAt(), "eligible immediately");
            // Nothing is sent here: this runs inside the caller's transaction, and a call
            // to Firebase would hold a database connection for its duration.
            verifyNoInteractions(notifications);
        }

        @Test
        @DisplayName("a category the gamer switched off is not queued")
        void respectsPreferences() {
            Gamer gamer = new Gamer();
            gamer.setNotifyCommunities(false);
            when(gamers.findByFcmToken("dev-1")).thenReturn(Optional.of(gamer));

            dispatcher.onNotificationRequested(
                    new NotificationRequestedEvent("dev-1", "T", "B", NotificationKind.POST_LIKE));

            // Enforced here rather than at the nine places that raise notifications: a
            // rule that has to be remembered nine times gets forgotten once, and the
            // failure mode is sending somebody exactly what they asked not to receive.
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("a category still on is queued as normal")
        void allowsWantedCategories() {
            Gamer gamer = new Gamer();
            gamer.setNotifyMessages(true);
            when(gamers.findByFcmToken("dev-1")).thenReturn(Optional.of(gamer));

            dispatcher.onNotificationRequested(
                    new NotificationRequestedEvent("dev-1", "T", "B", NotificationKind.MESSAGE));

            verify(repository).save(any(NotificationOutbox.class));
        }

        @Test
        @DisplayName("a token with no account behind it still goes out")
        void unknownTokenIsNotSilentlyDropped() {
            // The device was detached — reinstalled, or claimed by another account. The
            // send fails harmlessly at Firebase. Refusing here would swallow notifications
            // for a state we cannot tell apart from a race.
            when(gamers.findByFcmToken(anyString())).thenReturn(Optional.empty());

            dispatcher.onNotificationRequested(
                    new NotificationRequestedEvent("dev-1", "T", "B", NotificationKind.MESSAGE));

            verify(repository).save(any(NotificationOutbox.class));
        }

        @Test
        @DisplayName("a gamer with no device is not queued")
        void skipsMissingToken() {
            dispatcher.onNotificationRequested(new NotificationRequestedEvent(null, "T", "B", NotificationKind.MATCH));
            dispatcher.onNotificationRequested(new NotificationRequestedEvent("  ", "T", "B", NotificationKind.MATCH));

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("over-long text is truncated rather than failing the transaction")
        void truncatesOverlongText() {
            dispatcher.onNotificationRequested(
                    new NotificationRequestedEvent("dev-1", "x".repeat(500), "y".repeat(5000), NotificationKind.MATCH));

            ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
            verify(repository).save(captor.capture());
            // The column limits are 200 and 1000. A notification is not worth rolling back
            // the match that caused it.
            assertEquals(200, captor.getValue().getTitle().length());
            assertEquals(1000, captor.getValue().getBody().length());
        }
    }

    @Nested
    class Delivering {

        private final NotificationOutboxPoller poller = new NotificationOutboxPoller(repository, notifications, CLOCK);

        @Test
        void deliversAndMarksSent() {
            NotificationOutbox row = pending("dev-1");
            when(repository.claimPending(eq(NOW), any(Pageable.class))).thenReturn(List.of(row));

            poller.deliverPending();

            verify(notifications).sendToToken(any());
            assertEquals(NOW, row.getSentAt());
            assertEquals(0, row.getAttempts());
            verify(repository).saveAll(anyList());
        }

        @Test
        @DisplayName("a failure defers rather than loses, and backs off")
        void failureIsRetriedLater() {
            NotificationOutbox row = pending("dev-1");
            when(repository.claimPending(eq(NOW), any(Pageable.class))).thenReturn(List.of(row));
            doThrow(new IllegalStateException("FCM unavailable"))
                    .when(notifications)
                    .sendToToken(any());

            poller.deliverPending();

            assertNull(row.getSentAt(), "still owed");
            assertEquals(1, row.getAttempts());
            assertTrue(row.getNextAttemptAt().isAfter(NOW), "backed off");
            assertTrue(row.getLastError().contains("FCM unavailable"));
        }

        @Test
        @DisplayName("one bad row does not abandon the rest of the batch")
        void oneFailureDoesNotStopTheBatch() {
            NotificationOutbox bad = pending("dead-token");
            NotificationOutbox good = pending("dev-2");
            when(repository.claimPending(eq(NOW), any(Pageable.class))).thenReturn(List.of(bad, good));
            // doReturn, not doNothing: sendToToken returns a response, and doNothing is
            // only valid for a void method.
            doThrow(new IllegalStateException("unregistered"))
                    .doReturn(DefaultMessageResponse.of("sent"))
                    .when(notifications)
                    .sendToToken(any());

            poller.deliverPending();

            assertNull(bad.getSentAt());
            assertEquals(NOW, good.getSentAt(), "the second still went out");
        }

        @Test
        @DisplayName("attempts are bounded: a permanently dead token stops being retried")
        void givesUpAfterMaxAttempts() {
            NotificationOutbox row = pending("dead-token");
            row.setAttempts(NotificationOutboxPoller.MAX_ATTEMPTS - 1);
            when(repository.claimPending(eq(NOW), any(Pageable.class))).thenReturn(List.of(row));
            doThrow(new IllegalStateException("unregistered"))
                    .when(notifications)
                    .sendToToken(any());

            poller.deliverPending();

            assertEquals(NotificationOutboxPoller.MAX_ATTEMPTS, row.getAttempts());
            // Left unsent rather than deleted, so the failure stays visible.
            assertNull(row.getSentAt());
        }

        @Test
        void nothingPendingDoesNothing() {
            when(repository.claimPending(eq(NOW), any(Pageable.class))).thenReturn(List.of());

            poller.deliverPending();

            verifyNoInteractions(notifications);
            verify(repository, never()).saveAll(anyList());
        }
    }
}
