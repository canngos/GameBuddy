package com.gamebuddy.notif.domain.service.firebase;

import com.gamebuddy.notif.interfaces.enums.NotificationParameter;
import com.gamebuddy.notif.interfaces.request.SendNotificationTokenRequest;
import com.gamebuddy.notif.interfaces.request.SendNotificationTopicRequest;
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

        // Collapse on the kind and target, so ten messages from one person while the
        // phone is in a pocket arrive as one line rather than ten. Android replaces a
        // pending message with the same key instead of stacking it.
        message.setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setCollapseKey(collapseKey(tokenRequest))
                .build());

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
     * What counts as "the same notification" for collapsing.
     *
     * <p>Kind plus target: two messages from the same person collapse, a message and a
     * like do not, and a like from two different people on two different posts stays two
     * notifications. Falls back to the kind alone when there is no target.
     */
    private String collapseKey(SendNotificationTokenRequest request) {
        String kind = request.getKind() == null ? "general" : request.getKind();
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
