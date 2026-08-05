package com.gamebuddy.common.util;

/** Message templates shared across services. */
public final class Constants {

    private Constants() {}

    // --- Verification / password reset e-mails ------------------------------
    public static final String EMAIL_SUBJECT = "GameBuddy - Your email verification code: %s";
    public static final String EMAIL_TEXT = """
            Thank you for registering on GameBuddy!

            Here is your verification code: %s

            The code expires in %d minutes. If you did not request it, you can ignore this email.

            Have fun!""";

    public static final String EMAIL_SUBJECT_FORGOT_PASSWORD = "GameBuddy - Password Reset";
    public static final String EMAIL_TEXT_FORGOT_PASSWORD = """
            Someone requested a password reset for this account.

            Here is your verification code to continue: %s
            Your email: %s

            The code expires in %d minutes. If this was not you, just ignore this email \
            and nothing will happen.""";

    // --- Push notification copy ---------------------------------------------
    public static final String FRIEND_REQUEST_TITLE = "New friend request!";
    public static final String FRIEND_REQUEST_BODY = "%s sent you a friend request.";

    public static final String FRIEND_REQUEST_ACCEPTED_TITLE = "Friend request accepted!";
    public static final String FRIEND_REQUEST_ACCEPTED_BODY = "%s accepted your friend request.";

    /** Sent to both gamers the moment a match becomes mutual. */
    public static final String MATCH_TITLE = "It's a match!";

    public static final String MATCH_BODY = "You and %s liked each other. Say hello!";

    /** A chat message. The title is the sender, so the body can be what they said. */
    public static final String MESSAGE_BODY_FALLBACK = "Sent you a message";

    public static final String COMMUNITY_POST_TITLE = "New in %s";
    public static final String COMMUNITY_POST_BODY = "%s posted: %s";

    public static final String POST_LIKE_TITLE = "Someone liked your post";
    public static final String POST_LIKE_BODY = "%s liked your post.";

    public static final String COMMENT_LIKE_TITLE = "Someone liked your comment";
    public static final String COMMENT_LIKE_BODY = "%s liked your comment.";

    public static final String POST_COMMENT_TITLE = "New comment";
    public static final String POST_COMMENT_BODY = "%s commented on your post: %s";

    public static final String BADGE_TITLE = "Badge unlocked!";

    /** Names the reward, because claiming it is a second action the gamer has to take. */
    public static final String BADGE_BODY = "You earned '%s'. Open Badges to claim your coins.";
}
