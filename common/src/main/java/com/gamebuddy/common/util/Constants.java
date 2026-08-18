package com.gamebuddy.common.util;

/** Message templates shared across services. */
public final class Constants {

    private Constants() {}

    // --- Transactional e-mail copy -------------------------------------------
    //
    // Each message is a handful of pieces rather than one block of prose, because every
    // email goes out with an HTML body and a plain-text one and those two have to say the
    // same thing. Composed into an EmailContent at the call site and rendered twice; see
    // com.gamebuddy.shared.mail.EmailRenderer.
    //
    // English only, in all seven of the app's languages. Localising them needs a
    // MessageSource and a per-account language column, which is a larger change than it
    // looks and is not this one.

    /**
     * The code leads the subject line.
     *
     * <p>It is the whole content of the message and it is what makes the flow fast: on a
     * phone the notification alone is often enough to type it in without opening anything.
     * Standard practice for the same reason — every large service that mails a code puts it
     * here.
     */
    public static final String EMAIL_SUBJECT = "%s is your GameBuddy code";

    public static final String EMAIL_PREHEADER = "Confirm your address and your account is ready.";
    public static final String EMAIL_HEADING = "Confirm your email";
    public static final String EMAIL_INTRO = "Enter this code in the app to finish setting up your GameBuddy account.";
    public static final String EMAIL_CODE_CAPTION = "The code expires in %d minutes.";
    public static final String EMAIL_FOOTNOTE =
            "If you did not sign up for GameBuddy, ignore this email. The account cannot be used until this code "
                    + "is entered.";

    public static final String EMAIL_SUBJECT_FORGOT_PASSWORD = "%s is your GameBuddy password reset code";
    public static final String EMAIL_PREHEADER_FORGOT_PASSWORD = "Use this code to choose a new password.";
    public static final String EMAIL_HEADING_FORGOT_PASSWORD = "Reset your password";

    /** Names the address, because somebody with two accounts needs to know which one this is. */
    public static final String EMAIL_INTRO_FORGOT_PASSWORD =
            "Enter this code in the app to choose a new password for %s.";

    public static final String EMAIL_FOOTNOTE_FORGOT_PASSWORD =
            "If you did not ask for this, ignore this email and your password stays as it is.";

    /**
     * Sent after a password is reset, to the address whose password changed.
     *
     * <p>This is the only message that reaches somebody whose mailbox has been taken over:
     * everything else in the reset flow is visible to whoever is holding the mailbox, and
     * silence would let a takeover finish unremarked. It names no code and carries no link —
     * there is nothing here for an attacker to use, only something for an owner to notice.
     */
    public static final String EMAIL_SUBJECT_PASSWORD_CHANGED = "Your GameBuddy password was changed";

    public static final String EMAIL_PREHEADER_PASSWORD_CHANGED = "Every signed-in device has been signed out.";
    public static final String EMAIL_HEADING_PASSWORD_CHANGED = "Your password was changed";

    public static final String EMAIL_INTRO_PASSWORD_CHANGED =
            "The password for the GameBuddy account %s was just changed, and every device that was signed in "
                    + "has been signed out.";

    public static final String EMAIL_BODY_PASSWORD_CHANGED = "If that was you, there is nothing else to do.";

    public static final String EMAIL_FOOTNOTE_PASSWORD_CHANGED =
            "If it was not, somebody else can reach this mailbox. Reset the password again straight away, then "
                    + "secure your email account.";

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
