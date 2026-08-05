package com.gamebuddy.notif.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.notif.application.mapper.NotificationMapper;
import com.gamebuddy.notif.domain.service.firebase.FCMService;
import com.gamebuddy.notif.infrastructure.entity.Notification;
import com.gamebuddy.notif.infrastructure.repository.NotificationRepository;
import com.gamebuddy.notif.interfaces.dto.GetNotificationsResponseBody;
import com.gamebuddy.notif.interfaces.dto.NotificationPreferencesDto;
import com.gamebuddy.notif.interfaces.request.SendNotificationTokenRequest;
import com.gamebuddy.notif.interfaces.request.SendNotificationTopicRequest;
import com.gamebuddy.notif.interfaces.response.GetNotificationsResponse;
import com.gamebuddy.notif.interfaces.response.NotificationPreferencesResponse;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultNotificationService implements NotificationService {

    private final FCMService fcmService;
    private final NotificationRepository notificationRepository;
    private final GamerRepository gamerRepository;
    private final NotificationMapper notificationMapper;

    /**
     * Delivers a notification to one device and records it.
     *
     * <p>An unknown device token used to abort the whole call with USER_NOT_FOUND, so a
     * gamer who had reinstalled the app — leaving a stale token on the sending side —
     * silently stopped receiving anything, and the caller saw an error it could do
     * nothing about. The push is attempted regardless now; only the history row needs a
     * gamer to attribute it to.
     *
     * <p><strong>REQUIRES_NEW, and that is the whole fix for the outbox poller.</strong>
     *
     * <p>With the default propagation this joined the poller's transaction. A Firebase
     * failure threw out through this proxy, Spring marked the <em>shared</em> transaction
     * rollback-only, and the poller's own careful per-row error handling — raise the
     * attempt count, push out the next attempt — was writing into a transaction that
     * could no longer commit. Every run ended in UnexpectedRollbackException, every
     * attempt counter was rolled back, and the same rows were retried ten seconds later
     * forever. On a machine with Firebase switched off that was thousands of log lines a
     * minute and an outbox that never drained or gave up.
     *
     * <p>In its own transaction the failure rolls back only this method's history write,
     * which is what should happen, and the caller's bookkeeping survives.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DefaultMessageResponse sendToToken(SendNotificationTokenRequest tokenRequest) {
        Optional<Gamer> recipient = gamerRepository.findByFcmToken(tokenRequest.getToken());

        send(() -> fcmService.sendMessageToToken(tokenRequest));

        if (recipient.isPresent()) {
            saveHistory(
                    tokenRequest.getTitle(),
                    tokenRequest.getBody(),
                    recipient.get().getUserId(),
                    false);
        } else {
            log.warn("Delivered a notification to a device token that matches no gamer; not recording history");
        }
        return DefaultMessageResponse.of("Notification sent successfully");
    }

    /** Its own transaction too, for the reason given on {@link #sendToToken}. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DefaultMessageResponse sendToTopic(SendNotificationTopicRequest topicRequest) {
        send(() -> fcmService.sendMessageToTopic(topicRequest));
        saveHistory(topicRequest.getTitle(), topicRequest.getBody(), topicRequest.getTopic(), true);
        return DefaultMessageResponse.of("Notification to topic sent successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public GetNotificationsResponse showAll(Gamer principal, Pageable pageable) {
        var notifications = notificationRepository.findVisibleTo(principal.getUserId(), pageable);

        GetNotificationsResponse response = new GetNotificationsResponse();
        GetNotificationsResponseBody body = new GetNotificationsResponseBody();
        body.setUserNotifications(notificationMapper.toDtos(notifications));
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    private void saveHistory(String title, String body, String recipient, boolean isTopic) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRecipient(recipient);
        notification.setIsTopic(isTopic);
        notificationRepository.save(notification);
    }

    /**
     * Runs a Firebase send, translating its checked exceptions.
     *
     * <p>The previous version caught {@code Exception} and called
     * {@code Thread.currentThread().interrupt()} on every failure — including plain
     * delivery errors that have nothing to do with interruption. Setting the interrupt
     * flag on a pooled request thread leaves it set for whatever request the container
     * hands that thread next, which then fails for no visible reason. Only a genuine
     * {@link InterruptedException} restores the flag now.
     */
    private void send(FirebaseCall call) {
        try {
            call.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(TransactionCode.NOTIF_SEND_FAILED, e);
        } catch (ExecutionException | RuntimeException e) {
            // Message only, no stack trace. A rejected push is an ordinary outcome that
            // the caller already handles by retrying, and a batch of fifty printing fifty
            // stack traces every ten seconds buries everything else in the log — which is
            // exactly what it did.
            log.warn("Firebase rejected a notification: {}", e.getMessage());
            throw new BusinessException(TransactionCode.NOTIF_SEND_FAILED, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(Gamer principal) {
        return preferencesResponse(reloadGamer(principal));
    }

    /**
     * Replaces the whole set.
     *
     * <p>Answers with what was stored rather than with what was sent, so a screen showing
     * a switch as off is showing the row, not its own optimism.
     */
    @Override
    @Transactional
    public NotificationPreferencesResponse updatePreferences(Gamer principal, NotificationPreferencesDto wanted) {
        Gamer gamer = reloadGamer(principal);
        gamer.setNotifyMessages(wanted.isMessages());
        gamer.setNotifySocial(wanted.isSocial());
        gamer.setNotifyCommunities(wanted.isCommunities());
        gamer.setRemindersEnabled(wanted.isReminders());

        // Turning reminders back on clears the count, so somebody who switched them off
        // mid-absence and changed their mind is not immediately owed the nudges they
        // missed while they were off. Their absence starts again from now.
        if (wanted.isReminders()) {
            gamer.setNudgeCount(0);
        }

        gamerRepository.save(gamer);
        log.info("Gamer {} updated notification preferences", gamer.getUserId());
        return preferencesResponse(gamer);
    }

    private NotificationPreferencesResponse preferencesResponse(Gamer gamer) {
        NotificationPreferencesDto dto = new NotificationPreferencesDto(
                gamer.isNotifyMessages(),
                gamer.isNotifySocial(),
                gamer.isNotifyCommunities(),
                gamer.isRemindersEnabled());

        NotificationPreferencesResponse response = new NotificationPreferencesResponse();
        response.setBody(new BaseBody<>(dto));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /** The principal comes off a token and is detached; a write needs the managed row. */
    private Gamer reloadGamer(Gamer principal) {
        return gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }

    @FunctionalInterface
    private interface FirebaseCall {
        void run() throws ExecutionException, InterruptedException;
    }
}
