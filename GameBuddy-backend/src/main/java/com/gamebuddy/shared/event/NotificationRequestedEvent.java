package com.gamebuddy.shared.event;

/**
 * A push notification to deliver once the surrounding transaction commits.
 *
 * <p><strong>The recipient is identified by {@code recipientId}, not by the token.</strong>
 * A device token identifies a device; an account is what has preferences. Those are not the
 * same thing and must not be conflated: {@code NotificationDispatcher} used to resolve the
 * recipient with {@code findByFcmToken} to check whether they wanted this category, which
 * threw {@code NonUniqueResultException} the moment two accounts shared a token — and that
 * exception escaped through the publishing transaction and turned the originating request
 * into a 500. Accepting a match failed outright because of a preference lookup.
 *
 * @param recipientId account the notification is for; used to resolve preferences
 * @param fcmToken device token to deliver to; may be null if they never registered one
 * @param title notification title
 * @param body notification body
 * @param kind what it is about, so the client knows which screen to open
 * @param targetId the thing to open — a gamer id, a post id — or null when the kind needs
 *     no argument
 */
public record NotificationRequestedEvent(
        String recipientId, String fcmToken, String title, String body, NotificationKind kind, String targetId) {

    /** For kinds that open a screen needing no argument, such as the badge list. */
    public NotificationRequestedEvent(
            String recipientId, String fcmToken, String title, String body, NotificationKind kind) {
        this(recipientId, fcmToken, title, body, kind, null);
    }
}
