package com.gamebuddy.shared.storage;

import com.gamebuddy.shared.entity.Avatars;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.AvatarsRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The one answer to "what picture do I show for this gamer".
 *
 * <p>Four modules resolved that independently — profile, community, match and chat each
 * had a private {@code resolveAvatars} that looked up {@code gamer.avatar} in the
 * catalogue. Four copies of a rule is four places to forget a new one, and this change
 * adds exactly such a rule: an uploaded avatar is only visible once approved. Missing it
 * in one of the four would publish an unreviewed image on, say, the community feed while
 * the profile screen correctly hid it.
 *
 * <p>So the rule lives here, once:
 *
 * <ol>
 *   <li>an approved upload wins;
 *   <li>otherwise the legacy catalogue avatar, while that still exists;
 *   <li>otherwise nothing, and the client draws its coloured monogram.
 * </ol>
 *
 * <p>Returning {@code null} rather than a placeholder URL is deliberate. The client already
 * generates a distinct monogram per gamer, which is more useful than one grey silhouette
 * repeated down a list, and it does not cost a request that resolves to the same bytes
 * every time.
 */
@Service
@RequiredArgsConstructor
public class AvatarUrls {

    private final ObjectStorage storage;
    private final AvatarsRepository avatarsRepository;

    /** The URL others should see for one gamer, or null to fall back to the monogram. */
    public String visibleTo(Gamer gamer) {
        if (gamer == null) {
            return null;
        }
        if (gamer.hasVisibleAvatar()) {
            return storage.publicUrl(gamer.getAvatarKey());
        }
        return legacyImage(gamer.getAvatar());
    }

    /**
     * What the owner sees, which includes their own unapproved upload.
     *
     * <p>Someone who has just uploaded a picture and is being told it is under review
     * should be able to see the picture they uploaded. Hiding it from them as well would
     * make the wait look like a failure.
     */
    public String visibleToOwner(Gamer gamer) {
        if (gamer != null && gamer.getAvatarKey() != null) {
            return storage.publicUrl(gamer.getAvatarKey());
        }
        return visibleTo(gamer);
    }

    /**
     * Resolves a collection in one query rather than one per gamer.
     *
     * <p>The lists this feeds — a deck, an inbox, a member list — are the reason the
     * catalogue lookup was batched in the first place: a query per row turns a 20-item
     * feed into 21 round trips.
     *
     * @return gamer id to URL, with absent entries for anyone who falls back to a monogram
     */
    public Map<String, String> visibleTo(Collection<Gamer> gamers) {
        Map<String, String> resolved = new HashMap<>();

        Set<UUID> legacyIds = gamers.stream()
                .filter(g -> !g.hasVisibleAvatar())
                .map(Gamer::getAvatar)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> legacy = legacyIds.isEmpty()
                ? Map.of()
                : avatarsRepository.findAllByIdIn(legacyIds).stream()
                        .filter(a -> toUrl(a.getImage()) != null)
                        .collect(Collectors.toMap(Avatars::getId, a -> toUrl(a.getImage()), (a, b) -> a));

        for (Gamer gamer : gamers) {
            String url = gamer.hasVisibleAvatar()
                    ? storage.publicUrl(gamer.getAvatarKey())
                    : (gamer.getAvatar() == null ? null : legacy.get(gamer.getAvatar()));
            if (url != null) {
                resolved.put(gamer.getUserId(), url);
            }
        }
        return resolved;
    }

    private String legacyImage(UUID avatarId) {
        if (avatarId == null) {
            return null;
        }
        return avatarsRepository
                .findById(avatarId)
                .map(Avatars::getImage)
                .map(this::toUrl)
                .orElse(null);
    }

    /**
     * A catalogue row's {@code image} into something a client can fetch.
     *
     * <p>Three shapes exist in that column, because it has outlived two storage decisions:
     * a full URL from the Firebase era, a bare filename from when the art had nowhere to
     * live, and — now — an object key like {@code default-avatars/avatar-01.png}. Keys are
     * the only shape being written from here on: which host serves an image is a
     * deployment concern that changes, and a stored URL bakes today's answer into every
     * row.
     *
     * <p>A bare filename cannot be resolved and comes back as-is, which the client already
     * treats as unusable and falls back to a monogram for.
     */
    public String publicUrlFor(String image) {
        return toUrl(image);
    }

    /** See {@link #publicUrlFor(String)}. */
    private String toUrl(String image) {
        if (image == null || image.isBlank()) {
            return null;
        }
        if (image.startsWith("http://") || image.startsWith("https://")) {
            return image;
        }
        return image.contains("/") ? storage.publicUrl(image) : image;
    }
}
