package com.gamebuddy.profile.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

/** One mission, and how far this gamer has got with it. */
@Getter
@Setter
public class BadgeDto {

    /** Stable identifier; what {@code collect} and {@code showcase} are called with. */
    private String code;

    private String title;

    /** What to do to earn it. */
    private String description;

    /** A resolved URL, not the stored object key. */
    private String icon;

    /** How far along, capped at {@link #target} so the client never shows 12/10. */
    private int progress;

    private int target;

    /** Coins credited on collection. */
    private int reward;

    private boolean earned;

    /** Earned and the coins already claimed. False on an unearned badge. */
    private boolean collected;

    /** Whether this is one of the three on the gamer's profile. */
    private boolean showcased;
}
