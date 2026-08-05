package com.gamebuddy.shared.storage;

import com.gamebuddy.shared.entity.Cosmetic;
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

    /** The URL for one cosmetic, or null if the gamer is not wearing one. */
    public String urlFor(Cosmetic cosmetic) {
        return cosmetic == null || cosmetic.getAssetKey() == null ? null : storage.publicUrl(cosmetic.getAssetKey());
    }

    public String frameUrl(Gamer gamer) {
        return gamer == null ? null : urlFor(gamer.getEquippedFrame());
    }

    public String bannerUrl(Gamer gamer) {
        return gamer == null ? null : urlFor(gamer.getEquippedBanner());
    }
}
