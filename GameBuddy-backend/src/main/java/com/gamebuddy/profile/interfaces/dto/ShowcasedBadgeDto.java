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
}
