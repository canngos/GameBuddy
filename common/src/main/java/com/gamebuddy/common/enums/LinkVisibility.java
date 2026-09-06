package com.gamebuddy.common.enums;

/**
 * Who can see a linked account's handle on a profile.
 *
 * <p>A setting rather than a fixed rule, because the two reasonable answers are genuinely
 * both reasonable and they belong to different people. Somebody who streams wants their
 * Discord on the profile where anybody can read it; somebody who does not want strangers
 * from the deck arriving in their DMs wants it shown only to people they have agreed to
 * talk to. Choosing one for everybody would be wrong for half of them.
 *
 * <p>{@link #MATCHES} is the default, and defaults are the setting most people will
 * actually have. A contact detail should not reach strangers because somebody never found
 * this screen — that is the same principle {@code TextSurface} applies to typed text, and
 * the reasoning there is worth reading: a handle swapped between two matched adults is the
 * product working, and the same string broadcast to everyone is a different thing.
 */
public enum LinkVisibility {

    /** Anyone who can see the profile. */
    PUBLIC,

    /** Matches and friends only. The default. */
    MATCHES;

    /** @return the visibility, or null if the name is not one */
    public static LinkVisibility from(String name) {
        if (name == null) {
            return null;
        }
        for (LinkVisibility visibility : values()) {
            if (visibility.name().equalsIgnoreCase(name.trim())) {
                return visibility;
            }
        }
        return null;
    }
}
