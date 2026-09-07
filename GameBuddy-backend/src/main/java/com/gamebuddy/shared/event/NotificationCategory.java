package com.gamebuddy.shared.event;

import com.gamebuddy.shared.entity.Gamer;

/**
 * The switches a gamer actually gets in Settings.
 *
 * <p>Four, not one per {@link NotificationKind}. Nine toggles is a screen nobody reads;
 * the decision people want to make is "keep the messages, stop the community noise".
 *
 * <p>All four default to on, and every one of them can be turned off. That is deliberate
 * and it includes messages: an app that reserves the right to interrupt you about
 * something you said you did not want to hear about will simply have its notifications
 * disabled wholesale at the operating system, which costs it the ones that mattered too.
 */
public enum NotificationCategory {

    /** Somebody sent you a chat message. */
    MESSAGES,

    /** Matches, friend requests and answers to them, badges you earned, lobby activity. */
    SOCIAL,

    // COMMUNITIES retired with the Community feature; its gamer column went with it.

    /**
     * "Come back" nudges.
     *
     * <p>The one nobody asked for, and the one most likely to be switched off. That is
     * the correct outcome when somebody does not want it.
     */
    REMINDERS;

    /** Whether this gamer still wants this category. */
    public boolean wantedBy(Gamer gamer) {
        return switch (this) {
            case MESSAGES -> gamer.isNotifyMessages();
            case SOCIAL -> gamer.isNotifySocial();
            case REMINDERS -> gamer.isRemindersEnabled();
        };
    }
}
