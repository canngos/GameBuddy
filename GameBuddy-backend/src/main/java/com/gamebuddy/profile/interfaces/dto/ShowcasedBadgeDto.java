package com.gamebuddy.profile.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * A badge as it appears on a profile.
 *
 * <p>Deliberately not {@link BadgeDto}. What another gamer sees is the picture and what it
 * was for — not the progress, the reward, or whether the coins have been claimed, which
 * are the owner's business and would be sent to everyone who opened their profile.
 */
@Getter
@Setter
public class ShowcasedBadgeDto {

    private String code;
    private String title;
    private String description;

    /** A resolved URL, not the stored object key. */
    private String icon;

    /**
     * True when the artwork is animated WebP.
     *
     * <p>Worth the extra field on the smaller DTO: a showcase is the one place a badge is
     * shown to other people, and the two animated ones are the two hardest in the game. A
     * still first frame on somebody else's profile would waste the only surface where
     * showing off is the entire point.
     */
    private boolean animated;
}
