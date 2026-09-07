package com.gamebuddy.notif.domain.service.firebase;

import com.gamebuddy.notif.interfaces.enums.NotificationParameter;
import com.gamebuddy.notif.interfaces.request.SendNotificationTokenRequest;
import com.gamebuddy.notif.interfaces.request.SendNotificationTopicRequest;
import com.gamebuddy.shared.event.NotificationCategory;
import com.gamebuddy.shared.event.NotificationKind;
import com.google.firebase.messaging.*;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FCMService {

    /**
     * Off locally, and off is not a failure.
     *
     * <p>Without this the disabled case reached {@code FirebaseMessaging.getInstance()},
     * which throws because no app was initialised — so on any machine without Firebase
     * credentials every notification failed, burned five retries, and filled the log.
     * That is a configuration state being reported as an incident.
     *
     * <p>The same shape as {@code MailConfig}'s log mode: when the integration is
     * switched off the message is printed instead of sent, and the caller is told it
     * succeeded, because as far as this deployment is concerned it did.
     */
    @Value("${firebase.enabled:true}")
    private boolean enabled;

    public void sendMessageToToken(SendNotificationTokenRequest tokenRequest)
            throws ExecutionException, InterruptedException {
        Notification notification = Notification.builder()
                .setTitle(tokenRequest.getTitle())
                .setBody(tokenRequest.getBody())
                .setImage(tokenRequest.getImageUrl())
                .build();
        Message.Builder message =
                Message.builder().setToken(tokenRequest.getToken()).setNotification(notification);

        // The kind and target ride along as data. FCM delivers data with the notification
        // and the client reads it on tap; without it every notification can only reopen
        // the app wherever it was, which for "X sent you a message" is the one place it
        // must not be.
        if (tokenRequest.getKind() != null) {
            message.putData("kind", tokenRequest.getKind());
        }
        if (tokenRequest.getTargetId() != null) {
            message.putData("targetId", tokenRequest.getTargetId());
        }

        AndroidConfig.Builder android = AndroidConfig.builder().setPriority(priorityFor(tokenRequest.getKind()));

        // Collapse on the kind and target, so ten messages from one person while the
        // phone is in a pocket arrive as one line rather than ten. Android replaces a
        // pending message with the same key instead of stacking it.
        String collapseKey = collapseKey(tokenRequest);
        if (collapseKey != null) {
            android.setCollapseKey(collapseKey);
        }

        // Which channel it lands on, which on Android 8+ is what decides whether it appears
        // over whatever the gamer is doing or only in the shade. Unclassifiable kinds are
        // left unset on purpose: the app's manifest names a default channel, and letting it
        // decide beats naming a channel that might not exist on an older install.
        String channelId = channelIdFor(tokenRequest.getKind());
        if (channelId != null) {
            android.setNotification(
                    AndroidNotification.builder().setChannelId(channelId).build());
        }

        message.setAndroidConfig(android.build());

        sendAndGetResponse(message.build());
        log.debug("Notification delivered to a device token");
    }

    public void sendMessageToTopic(SendNotificationTopicRequest topicRequest)
            throws ExecutionException, InterruptedException {
        Notification notification = Notification.builder()
                .setTitle(topicRequest.getTitle())
                .setBody(topicRequest.getBody())
                .setImage(topicRequest.getImageUrl())
                .build();
        String topic = topicRequest.getTopic();
        Message message = Message.builder()
                .setApnsConfig(getApnsConfig(topic))
                .setAndroidConfig(getAndroidConfig(topic))
                .setNotification(notification)
                .setTopic(topic)
                .build();
        sendAndGetResponse(message);
        log.info("Notification broadcast to topic {}", topic);
    }

    /**
     * The Android channel a kind belongs on, or null when it cannot be classified.
     *
     * <p>The categories are the ones {@link NotificationKind#category()} already defines for
     * the per-account settings toggles, so the switch a gamer sees in the app and the
     * channel Android gives them control of describe the same three groups rather than two
     * different carvings of the same nine kinds.
     *
     * <p><strong>These ids are a contract with the client</strong>, which creates the
     * channels at launch — see {@code src/notifications/channel.ts} in GameBuddy-App. A
     * channel named here that the device has not created is not shown as we intend; the FCM
     * SDK falls back to the manifest's default channel. That fallback is why this is safe to
     * deploy, but the client build should still go out first.
     *
     * <p>An unknown or missing kind returns null rather than guessing. A build that adds a
     * kind and forgets this switch then delivers on the manifest default, which is a quiet
     * notification rather than a missing one.
     */
    private String channelIdFor(String kind) {
        NotificationCategory category = categoryOf(kind);
        if (category == null) {
            return null;
        }
        return switch (category) {
            case MESSAGES -> "messages";
            case SOCIAL -> "social";
            case REMINDERS -> "reminders";
        };
    }

    /**
     * How urgently FCM should wake the device.
     *
     * <p>HIGH for anything somebody caused — a message, a match, a friend request — because
     * those are worth delivering now. NORMAL for the "come back" nudge, which may wait for
     * the device to leave Doze on its own: it is the one notification nobody asked for, and
     * spending a wakeup on it is how an app earns a battery warning.
     */
    private AndroidConfig.Priority priorityFor(String kind) {
        return categoryOf(kind) == NotificationCategory.REMINDERS
                ? AndroidConfig.Priority.NORMAL
                : AndroidConfig.Priority.HIGH;
    }

    /** The category a kind belongs to, or null when this build does not recognise it. */
    private NotificationCategory categoryOf(String kind) {
        if (kind == null) {
            return null;
        }
        try {
            return NotificationKind.valueOf(kind).category();
        } catch (IllegalArgumentException unknownKind) {
            log.warn("No notification category for kind {}; falling back to the app's default channel", kind);
            return null;
        }
    }

    /**
     * What counts as "the same notification" for collapsing, or null to never collapse.
     *
     * <p>Only conversation traffic collapses. Kind plus target: two messages from the same
     * person arrive as one line, a message and a match do not, and two people writing at
     * once stay two notifications. That is the case collapsing was meant for — a stream of
     * updates where only the latest matters.
     *
     * <p><b>Everything else is sent uncollapsed</b>, because those kinds are single events
     * rather than a running total, and FCM's collapse rule is "replace the pending one",
     * not "merge them". A gamer who withdrew a friend request and sent it again produced
     * two notifications one key apart; with the phone asleep the second replaced the first
     * at FCM and the recipient was shown a request they had already been shown — which,
     * once the first was dismissed, is a request that never appeared at all. There is only
     * ever one live friend request between two people, so there is nothing to collapse and
     * a whole class of vanishing notifications to avoid.
     */
    private String collapseKey(SendNotificationTokenRequest request) {
        if (categoryOf(request.getKind()) != NotificationCategory.MESSAGES) {
            return null;
        }
        String kind = request.getKind();
        return request.getTargetId() == null ? kind : kind + ":" + request.getTargetId();
    }

    private String sendAndGetResponse(Message message) throws InterruptedException, ExecutionException {
        if (!enabled) {
            log.info("FIREBASE OFF: notification not sent");
            return "firebase-disabled";
        }
        return FirebaseMessaging.getInstance().sendAsync(message).get();
    }

    private AndroidConfig getAndroidConfig(String topic) {
        return AndroidConfig.builder()
                .setTtl(Duration.ofMinutes(2).toMillis())
                .setCollapseKey(topic)
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setSound(NotificationParameter.SOUND.getValue())
                        .setColor(NotificationParameter.COLOR.getValue())
                        .setTag(topic)
                        .build())
                .build();
    }

    private ApnsConfig getApnsConfig(String topic) {
        return ApnsConfig.builder()
                .setAps(Aps.builder().setCategory(topic).setThreadId(topic).build())
                .build();
    }
}
