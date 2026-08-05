package com.gamebuddy.notif.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.notif.application.mapper.NotificationMapper;
import com.gamebuddy.notif.application.mapper.NotificationMapperImpl;
import com.gamebuddy.notif.domain.service.firebase.FCMService;
import com.gamebuddy.notif.infrastructure.entity.Notification;
import com.gamebuddy.notif.infrastructure.repository.NotificationRepository;
import com.gamebuddy.notif.interfaces.request.SendNotificationTokenRequest;
import com.gamebuddy.notif.interfaces.request.SendNotificationTopicRequest;
import com.gamebuddy.notif.interfaces.response.GetNotificationsResponse;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultNotificationServiceTest {

    @InjectMocks
    private DefaultNotificationService notificationService;

    @Mock
    private FCMService fcmService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private GamerRepository gamerRepository;

    @Spy
    private NotificationMapper notificationMapper = new NotificationMapperImpl();

    private static final Pageable PAGE = PageRequest.of(0, 50);

    private Gamer gamer;

    @BeforeEach
    void setUp() {
        gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setEmail("me@example.com");
        gamer.setGamerUsername("me");
        gamer.setFcmToken("device-token");
    }

    private static SendNotificationTokenRequest tokenRequest() {
        SendNotificationTokenRequest r = new SendNotificationTokenRequest();
        r.setToken("device-token");
        r.setTitle("Title");
        r.setBody("Body");
        return r;
    }

    private static SendNotificationTopicRequest topicRequest() {
        SendNotificationTopicRequest r = new SendNotificationTopicRequest();
        r.setTopic("all");
        r.setTitle("Broadcast");
        r.setBody("Body");
        return r;
    }

    @Nested
    class SendToToken {

        @Test
        void testSendToToken_whenTokenBelongsToAGamer_SendsAndRecords() throws Exception {
            when(gamerRepository.findByFcmToken("device-token")).thenReturn(Optional.of(gamer));

            DefaultMessageResponse response = notificationService.sendToToken(tokenRequest());

            assertEquals("100", response.getStatus().getCode());
            verify(fcmService).sendMessageToToken(any());
            verify(notificationRepository)
                    .save(argThat(n -> n.getRecipient().equals(gamer.getUserId()) && !n.getIsTopic()));
        }

        @Test
        @DisplayName("an unrecognised device token still gets the push; only the history row is skipped")
        void testSendToToken_whenTokenIsUnknown_StillDelivers() throws Exception {
            when(gamerRepository.findByFcmToken(anyString())).thenReturn(Optional.empty());

            DefaultMessageResponse response = notificationService.sendToToken(tokenRequest());

            // This used to throw USER_NOT_FOUND before even attempting delivery, so a
            // gamer who had reinstalled the app silently stopped receiving anything.
            assertEquals("100", response.getStatus().getCode());
            verify(fcmService).sendMessageToToken(any());
            verify(notificationRepository, never()).save(any());
        }

        @Test
        void testSendToToken_whenFirebaseFails_ReturnErrorCode114() throws Exception {
            when(gamerRepository.findByFcmToken(anyString())).thenReturn(Optional.of(gamer));
            doThrow(new ExecutionException("rejected", null)).when(fcmService).sendMessageToToken(any());
            SendNotificationTokenRequest request = tokenRequest();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> notificationService.sendToToken(request));
            assertEquals(114, ex.getTransactionCode().getId());
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("a delivery failure does not leave the request thread interrupted")
        void testSendToToken_whenFirebaseFails_DoesNotInterruptTheThread() throws Exception {
            when(gamerRepository.findByFcmToken(anyString())).thenReturn(Optional.of(gamer));
            doThrow(new ExecutionException("rejected", null)).when(fcmService).sendMessageToToken(any());
            SendNotificationTokenRequest request = tokenRequest();

            assertThrows(BusinessException.class, () -> notificationService.sendToToken(request));

            // The old catch block called Thread.currentThread().interrupt() for every
            // exception, leaving the flag set on a pooled thread for whatever request
            // the container handed it next.
            assertFalse(Thread.currentThread().isInterrupted());
        }

        @Test
        @DisplayName("a genuine interruption does restore the flag")
        void testSendToToken_whenInterrupted_RestoresTheFlag() throws Exception {
            when(gamerRepository.findByFcmToken(anyString())).thenReturn(Optional.of(gamer));
            doThrow(new InterruptedException("interrupted")).when(fcmService).sendMessageToToken(any());
            SendNotificationTokenRequest request = tokenRequest();

            assertThrows(BusinessException.class, () -> notificationService.sendToToken(request));

            assertTrue(
                    Thread.interrupted(), "the interrupt flag must be restored, and is cleared here for later tests");
        }
    }

    @Nested
    class SendToTopic {

        @Test
        void testSendToTopic_whenCalled_SendsAndRecordsAsTopic() throws Exception {
            DefaultMessageResponse response = notificationService.sendToTopic(topicRequest());

            assertEquals("100", response.getStatus().getCode());
            verify(fcmService).sendMessageToTopic(any());
            verify(notificationRepository)
                    .save(argThat(n -> n.getIsTopic() && n.getRecipient().equals("all")));
        }

        @Test
        void testSendToTopic_whenFirebaseFails_ReturnErrorCode114() throws Exception {
            doThrow(new ExecutionException("rejected", null)).when(fcmService).sendMessageToTopic(any());
            SendNotificationTopicRequest request = topicRequest();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> notificationService.sendToTopic(request));
            assertEquals(114, ex.getTransactionCode().getId());
        }
    }

    @Nested
    class ShowAll {

        private Notification notification(String recipient, boolean isTopic, String title) {
            Notification n = new Notification();
            n.setId(UUID.randomUUID());
            n.setTitle(title);
            n.setBody("Body");
            n.setRecipient(recipient);
            n.setIsTopic(isTopic);
            n.setCreatedDate(Instant.now());
            return n;
        }

        @Test
        @DisplayName("the history is the caller's own plus broadcasts, in one ordered query")
        void testShowAll_whenCalled_ReturnsOwnAndBroadcasts() {
            when(notificationRepository.findVisibleTo(gamer.getUserId(), PAGE))
                    .thenReturn(List.of(
                            notification(gamer.getUserId(), false, "Personal"),
                            notification("all", true, "Broadcast")));

            GetNotificationsResponse response = notificationService.showAll(gamer, PAGE);
            var list = response.getBody().getData().getUserNotifications();

            assertEquals("100", response.getStatus().getCode());
            assertEquals(2, list.size());
            assertEquals("Personal", list.get(0).getTitle());
            assertEquals(gamer.getUserId(), list.get(0).getUserIdOrTopic());
            assertTrue(list.get(1).getIsTopic());
        }

        @Test
        @DisplayName("the query is scoped to the authenticated gamer, not an id from the path")
        void testShowAll_whenCalled_ScopesToThePrincipal() {
            when(notificationRepository.findVisibleTo(anyString(), any())).thenReturn(List.of());

            notificationService.showAll(gamer, PAGE);

            verify(notificationRepository).findVisibleTo(gamer.getUserId(), PAGE);
        }
    }
}
