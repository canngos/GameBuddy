package com.gamebuddy.shared.moderation;

/**
 * Who can read the text being screened, which decides how much is taken out of it.
 *
 * <p>The difference matters because of what GameBuddy is for. Two matched adults swapping
 * Discord tags is the product working — it is the entire point of an app that helps people
 * find someone to play with — so a filter that redacted contact details out of private
 * chat would be breaking the feature it was installed to protect. The same string in a
 * public post is a different thing: broadcast to strangers, indexed by whoever is
 * watching, and no longer under the control of the person who wrote it.
 */
public enum TextSurface {

    /** One-to-one chat between two people who have matched. Profanity only. */
    PRIVATE,

    /**
     * Anything a stranger can read without being matched: posts, comments, community
     * names and descriptions, usernames. Profanity, plus contact details that should not
     * be published to an audience.
     */
    PUBLIC
}
