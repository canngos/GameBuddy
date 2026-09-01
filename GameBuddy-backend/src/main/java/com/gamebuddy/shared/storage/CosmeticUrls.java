package com.gamebuddy.shared.storage;

import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.CosmeticKind;
import com.gamebuddy.shared.entity.Gamer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Asset keys to URLs, for the things a gamer wears.
 *
 * <p>The same rule as {@link AvatarUrls}, for the same reason: rows store object keys, not
 * URLs, because which host serves an image is a deployment concern that has already changed
 * twice and a stored URL bakes today's answer into every row.
 *
 * <p>Unlike avatars there is no fallback. Wearing nothing is the default and a perfectly
 * good look, so null propagates all the way to the client, which draws the avatar bare.
 */
@Service
@RequiredArgsConstructor
public class CosmeticUrls {

    private final ObjectStorage storage;

    /**
     * The URL for one cosmetic, or null if the gamer is not wearing one.
     *
     * <p>Null for a {@code THEME} as well: those carry a slug rather than a file, and
     * minting a URL out of it would produce a link to nothing. Use {@link #slug} instead.
     */
    public String urlFor(Cosmetic cosmetic) {
        if (cosmetic == null || cosmetic.getAssetKey() == null || cosmetic.getKind() == CosmeticKind.THEME) {
            return null;
        }
        return storage.publicUrl(cosmetic.getAssetKey());
    }

    public String frameUrl(Gamer gamer) {
        return gamer == null ? null : urlFor(gamer.getEquippedFrame());
    }

    public String bannerUrl(Gamer gamer) {
        return gamer == null ? null : urlFor(gamer.getEquippedBanner());
    }

    /**
     * A theme's slug — its asset key without the {@code themes/} prefix.
     *
     * <p>This is the whole wire format for a theme. The client holds the colours and looks
     * them up by slug; see {@link CosmeticKind#THEME} for why they are not stored here.
     */
    public String slug(Cosmetic cosmetic) {
        if (cosmetic == null || cosmetic.getKind() != CosmeticKind.THEME || cosmetic.getAssetKey() == null) {
            return null;
        }
        String key = cosmetic.getAssetKey();
        int slash = key.lastIndexOf('/');
        return slash < 0 ? key : key.substring(slash + 1);
    }

    /** The slug of the theme this gamer wears, or null. */
    public String themeOf(Gamer gamer) {
        return gamer == null ? null : slug(gamer.getEquippedTheme());
    }
}
