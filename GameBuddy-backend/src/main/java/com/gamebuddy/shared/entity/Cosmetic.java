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
