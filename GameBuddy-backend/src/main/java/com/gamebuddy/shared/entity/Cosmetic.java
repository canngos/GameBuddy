package com.gamebuddy.shared.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * A frame or a banner: something a gamer wears rather than something they are.
 *
 * <p>This replaces paid avatars. Selling a picture stopped making sense the moment gamers
 * could upload their own — the good avatars are the ones people bring themselves, and a
 * stock image everyone else can also buy is a weak thing to charge for. A frame composes
 * with whatever picture someone already chose instead of competing with it, which means
 * buying one never asks anybody to give up their own face.
 */
@Entity
@Table(name = "cosmetic")
// On the class rather than on Gamer's two references to it, because Hibernate rejects
// @BatchSize on a to-one attribute. Declared here it covers every lazy reference at once:
// a deck page of fifty gamers resolves both worn slots in one extra query instead of a
// hundred, which is the same N+1 the avatar catalogue lookup had to be rescued from.
@BatchSize(size = 50)
@Getter
@Setter
@NoArgsConstructor
public class Cosmetic implements Serializable {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CosmeticKind kind;

    @Column(nullable = false, length = 80)
    private String name;

    /**
     * An object key, not a URL.
     *
     * <p>Same reasoning as {@code gamer.avatarKey}: which host serves these is a deployment
     * concern that has already changed twice, and a stored URL bakes today's answer into
     * every row. Resolved on the way out by {@code CosmeticUrls}.
     */
    @Column(name = "asset_key", nullable = false)
    private String assetKey;

    /**
     * Whether the asset moves.
     *
     * <p>Not a rendering switch — {@code expo-image} plays animated WebP and stills through
     * the same component, and a still is an animation of length one. It exists so the store
     * can label and group them, because "animated" is the thing someone is actually paying
     * the difference for.
     */
    @Column(nullable = false)
    private boolean animated;

    /** In coins. Zero means everyone owns it; see the ownership rule in {@code CosmeticService}. */
    @Column(nullable = false)
    private int price;

    /**
     * Position on the shelf.
     *
     * <p>Explicit because both obvious automatic orders are wrong: cheapest-first buries
     * the new arrivals nobody has seen yet, and newest-first churns the whole store every
     * time one item is added.
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Arrives with a Gold membership and is withdrawn when it lapses. Never for sale.
     *
     * <p>Distinct from {@link #isFree()}, which means "everybody has this" — the opposite
     * claim. A membership item is owned by a subset of accounts and that subset changes,
     * which is exactly what makes it worth wearing: it is only a signal while it is true.
     */
    @Column(name = "membership_only", nullable = false)
    private boolean membershipOnly;

    /**
     * The {@code Badge.code} that grants this, or null for anything on sale.
     *
     * <p>A third way to not be for sale, and the only one that is permanent: {@link #isFree()}
     * means everybody has it, {@link #membershipOnly} means it comes and goes with a
     * subscription, and this means it was earned once and is kept forever.
     *
     * <p>The pointer runs this way — cosmetic names badge, not badge names cosmetic — so
     * there is exactly one place that says which frame goes with which award. {@code Badge}
     * only knows that it pays no coins; a slug there as well would be a second copy of the
     * same fact, and two copies is how a badge ends up granting the wrong thing.
     *
     * <p>These rows are priced zero. That is never visible as "Free" because the shop
     * filters them out before pricing anything, on the same path that already hides
     * membership items.
     */
    @Column(name = "unlocked_by_badge", length = 48)
    private String unlockedByBadge;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate = Instant.now();

    /** Free items need no purchase record; see {@code GamerCosmetic}. */
    public boolean isFree() {
        return price <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Cosmetic other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
