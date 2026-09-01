package com.gamebuddy.shared.event;

/**
 * What a notification is about, so that tapping it opens the right screen.
 *
 * <p>Sent to the device as a data field alongside the title and body. Without it every
 * notification can only open the app at whatever screen it was last on, which for a
 * message from somebody is the one thing it should never do — the whole value of the
 * notification is being one tap from the conversation.
 *
 * <p>The client maps each of these to a route and uses {@code targetId} to fill in the
 * parameter. A kind the installed app does not recognise must open the home screen rather
 * than fail: an old build will meet new kinds, and that is not a reason to do nothing.
 */
public enum NotificationKind {

    /** Both sides said yes. {@code targetId} is the other gamer. */
    MATCH,

    /**
     * Somebody spent a super like on this gamer. {@code targetId} is the sender.
     *
     * <p>Its own kind rather than reusing MATCH, because it is not a match — nothing is
     * open yet, and telling somebody they matched when they have not is the kind of
     * notification that gets an app muted.
     */
    SUPER_LIKE,

    /** A chat message arrived. {@code targetId} is the sender. */
    MESSAGE,

    /** Somebody asked to be friends. {@code targetId} is the sender. */
    FRIEND_REQUEST,

    /** A friend request was accepted. {@code targetId} is the accepter. */
    FRIEND_ACCEPTED,

    /** A mission was completed. No target — the badges screen shows them all. */
    BADGE,

    // COMMUNITY_POST, POST_LIKE, POST_COMMENT and COMMENT_LIKE retired with the Community
    // feature. Safe to remove outright: notification_outbox stores the kind as a plain
    // varchar, so old rows survive, and an installed app meeting an unknown kind routes
    // to home by design.

    /** Somebody asked to join this gamer's lobby. {@code targetId} is the lobby. */
    LOBBY_JOIN_REQUEST,

    /** The owner said yes. {@code targetId} is the lobby. */
    LOBBY_REQUEST_ACCEPTED,

    /**
     * A message in a lobby this gamer is in. {@code targetId} is the lobby.
     *
     * <p>There is deliberately no LOBBY_REQUEST_REJECTED — a rejection push is an invitation
     * to retaliate and carries nothing actionable — and no LOBBY_LOCKED: locking exists to
     * make things quieter, so announcing it would be the feature contradicting itself.
     */
    LOBBY_MESSAGE,

    /** A lobby this gamer was accepted into is off. {@code targetId} is the lobby. */
    LOBBY_CANCELLED,

    /**
     * An administrator put a promotion code on this account. No target — the promotion
     * codes screen in Settings lists everything waiting.
     *
     * <p>Under REMINDERS rather than SOCIAL: nobody did anything to this gamer, and the
     * switch somebody flips to stop hearing from the app itself is the one that should
     * silence it.
     */
    PROMO,

    /**
     * A nudge to somebody who has not opened the app in a while.
     *
     * <p>The only kind the gamer did not cause. Everything else here is a response to
     * something somebody did to them, which is a different bargain.
     */
    RETURN;

    /**
     * Which switch in Settings controls this kind.
     *
     * <p>Four groups rather than nine toggles. A settings screen with one row per
     * notification type is a screen nobody reads: the choice a person actually wants to
     * make is "keep messages, stop the community noise", not a decision about likes
     * separately from comments.
     */
    public NotificationCategory category() {
        return switch (this) {
            case MESSAGE, LOBBY_MESSAGE -> NotificationCategory.MESSAGES;
            // Lobby kinds sit under SOCIAL/MESSAGES rather than a category of their own:
            // the existing toggles already carry the right meaning, and the retired
            // COMMUNITIES category was not reused because it held people's old "mute
            // community noise" choice, which must not silently apply to a new feature.
            case MATCH,
                    SUPER_LIKE,
                    FRIEND_REQUEST,
                    FRIEND_ACCEPTED,
                    BADGE,
                    LOBBY_JOIN_REQUEST,
                    LOBBY_REQUEST_ACCEPTED,
                    LOBBY_CANCELLED -> NotificationCategory.SOCIAL;
            case RETURN, PROMO -> NotificationCategory.REMINDERS;
        };
    }
}
