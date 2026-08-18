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

    /**
     * Sent after a password is reset, to the address whose password changed.
     *
     * <p>This is the only message that reaches somebody whose mailbox has been taken over:
     * everything else in the reset flow is visible to whoever is holding the mailbox, and
     * silence would let a takeover finish unremarked. It names no code and carries no link —
     * there is nothing here for an attacker to use, only something for an owner to notice.
     */
    public static final String EMAIL_SUBJECT_PASSWORD_CHANGED = "GameBuddy - Your password was changed";

    public static final String EMAIL_TEXT_PASSWORD_CHANGED = """
            The password for your GameBuddy account was just changed, and every device that \
            was signed in has been signed out.

            Your email: %s

            If this was you, there is nothing to do.

            If it was not, someone else has access to this mailbox. Reset the password again \
            straight away and secure your email account.""";

    // --- Push notification copy ---------------------------------------------
    public static final String FRIEND_REQUEST_TITLE = "New friend request!";
    public static final String FRIEND_REQUEST_BODY = "%s sent you a friend request.";

    public static final String FRIEND_REQUEST_ACCEPTED_TITLE = "Friend request accepted!";
    public static final String FRIEND_REQUEST_ACCEPTED_BODY = "%s accepted your friend request.";

    /** Sent to both gamers the moment a match becomes mutual. */
    public static final String SUPER_LIKE_TITLE = "Someone really likes you";

    /** No name: a super like from a stranger is a reason to open the app, not a spoiler. */
    public static final String SUPER_LIKE_BODY = "You have a Super Like waiting.";

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
