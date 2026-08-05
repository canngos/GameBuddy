package com.gamebuddy.shared.entity;

/**
 * The two things a gamer can wear.
 *
 * <p>A closed enum rather than a free-text category because the two are rendered by
 * different code in different places — a frame composites around an avatar, a banner sits
 * behind a profile header — so a third value would have nowhere to be drawn. When there is
 * a third kind there will also be a renderer for it, and both changes belong in the same
 * commit.
 */
public enum CosmeticKind {
    /** Rings the avatar. Static PNG or animated WebP, always square with a clear centre. */
    FRAME,

    /** Sits behind the profile header. A wide still image. */
    BANNER
}
