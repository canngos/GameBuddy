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

    /** A chat message arrived. {@code targetId} is the sender. */
    MESSAGE,

    /** Somebody asked to be friends. {@code targetId} is the sender. */
    FRIEND_REQUEST,

    /** A friend request was accepted. {@code targetId} is the accepter. */
    FRIEND_ACCEPTED,

    /** A mission was completed. No target — the badges screen shows them all. */
    BADGE,

    /** Somebody posted in a community this gamer belongs to. {@code targetId} is the post. */
    COMMUNITY_POST,

    /** Somebody liked this gamer's post. {@code targetId} is the post. */
    POST_LIKE,

    /** Somebody commented on this gamer's post. {@code targetId} is the post. */
    POST_COMMENT,

    /** Somebody liked this gamer's comment. {@code targetId} is the post it is on. */
    COMMENT_LIKE,

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
            case MESSAGE -> NotificationCategory.MESSAGES;
            case MATCH, FRIEND_REQUEST, FRIEND_ACCEPTED, BADGE -> NotificationCategory.SOCIAL;
            case COMMUNITY_POST, POST_LIKE, POST_COMMENT, COMMENT_LIKE -> NotificationCategory.COMMUNITIES;
            case RETURN -> NotificationCategory.REMINDERS;
        };
    }
}
