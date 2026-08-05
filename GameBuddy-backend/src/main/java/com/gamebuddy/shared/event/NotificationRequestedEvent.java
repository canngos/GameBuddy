package com.gamebuddy.shared.event;

/**
 * A push notification to deliver once the surrounding transaction commits.
 *
 * @param fcmToken device token of the recipient; may be null if they never registered one
 * @param title notification title
 * @param body notification body
 * @param kind what it is about, so the client knows which screen to open
 * @param targetId the thing to open — a gamer id, a post id — or null when the kind needs
 *     no argument
 */
public record NotificationRequestedEvent(
        String fcmToken, String title, String body, NotificationKind kind, String targetId) {

    /** For kinds that open a screen needing no argument, such as the badge list. */
    public NotificationRequestedEvent(String fcmToken, String title, String body, NotificationKind kind) {
        this(fcmToken, title, body, kind, null);
    }
}
