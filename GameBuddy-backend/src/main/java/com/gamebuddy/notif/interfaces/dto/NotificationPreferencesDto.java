package com.gamebuddy.notif.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Which categories of notification a gamer wants.
 *
 * <p>The same shape goes both ways: the settings screen reads it, flips one switch and
 * sends the whole thing back. Sending the whole set rather than a delta means the screen
 * and the server cannot end up disagreeing about a switch that was never mentioned.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesDto implements BaseModel {

    /** Chat messages. */
    private boolean messages;

    /** Matches, friend requests and answers, badges earned — lobby activity included. */
    private boolean social;

    /** "Come back" nudges. */
    private boolean reminders;
}
