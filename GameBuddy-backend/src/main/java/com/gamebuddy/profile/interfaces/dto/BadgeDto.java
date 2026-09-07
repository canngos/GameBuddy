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

    /**
     * Coins credited on collection.
     *
     * <p>Zero on the four hard badges that hand over a frame instead — see
     * {@link #cosmeticName}. It is never zero on anything else, so the client can treat
     * "reward is zero" and "there is a cosmetic" as the same state without checking both.
     */
    private int reward;

    /** BRONZE, SILVER, GOLD or PRISMATIC. Decides the rim art, and whether the app animates it. */
    private String tier;

    /**
     * True when the artwork is animated WebP rather than a still PNG.
     *
     * <p>Only two badges. Sent rather than derived from {@link #tier} because it is a
     * property of the file, not of the difficulty — and because the client needs to know
     * whether to pass {@code autoplay}, which is a rendering decision and should not depend
     * on the app guessing what the backend uploaded.
     */
    private boolean animated;

    /**
     * The frame this unlocks, for the badges that pay in cosmetics rather than coins.
     *
     * <p>Null for everything else. Resolved server-side because only the backend knows
     * which cosmetic row claims which badge, and the sheet has to be able to say what is
     * actually on offer rather than "a reward".
     */
    private String cosmeticName;

    private boolean earned;

    /** Earned and the coins already claimed. False on an unearned badge. */
    private boolean collected;

    /** Whether this is one of the three on the gamer's profile. */
    private boolean showcased;
}
