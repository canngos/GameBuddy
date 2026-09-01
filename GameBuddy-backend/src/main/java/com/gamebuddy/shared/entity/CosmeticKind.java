package com.gamebuddy.shared.entity;

/**
 * The things a gamer can wear.
 *
 * <p>A closed enum rather than a free-text category because each is rendered by different
 * code in different places, so a value with no renderer would have nowhere to be drawn. A
 * new kind and the code that draws it belong in the same commit.
 */
public enum CosmeticKind {
    /** Rings the avatar. Static PNG or animated WebP, always square with a clear centre. */
    FRAME,

    /** Sits behind the profile header. A wide image, still or animated. */
    BANNER,

    /**
     * Colours the gamer's card wherever other people meet it — the deck, the candidate
     * sheet, the admirer tile — and the profile header strip when no banner is worn.
     *
     * <p><strong>The only kind with no image.</strong> A theme is a pair of colour stops,
     * and they live in the app (`src/theme/cardThemes.js`) rather than in the database: the
     * contrast gate can only check colours it can see at build time, and this is exactly
     * the surface where a bad pair makes a username unreadable. The row carries a slug in
     * {@code assetKey} and nothing else, so {@code image} is null for these.
     */
    THEME
}
