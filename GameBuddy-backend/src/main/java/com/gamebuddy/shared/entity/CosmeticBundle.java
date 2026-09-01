package com.gamebuddy.shared.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A set of cosmetics sold together for less than the sum of its parts.
 *
 * <p><strong>A bundle is a price, not a possession.</strong> Buying one writes exactly the
 * {@code gamer_cosmetic} rows that buying the parts separately would, so nothing downstream
 * — equipping, the membership sweep, the badge that counts owned cosmetics — has to learn
 * that bundles exist. There is deliberately no "owned bundle" row: a gamer owns items, and
 * whether they came as a set is a fact about the receipt, which
 * {@code gamer_cosmetic.paid} already records per item.
 *
 * <p>That is also why the discount is applied by splitting the bundle price across those
 * receipts rather than by storing a discount somewhere: if the items are ever repriced,
 * what each one actually cost this gamer is still on the row, exactly as
 * {@code upgrade-2026-18}'s refund relies on.
 */
@Entity
@Table(name = "cosmetic_bundle")
@Getter
@Setter
@NoArgsConstructor
public class CosmeticBundle implements Serializable {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String name;

    /** What the set costs in coins, below the sum of the parts. */
    @Column(nullable = false)
    private int price;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Eager, unlike the cosmetics' own lazy references.
     *
     * <p>A bundle is never useful without knowing what is in it — the shelf draws the parts
     * and the checkout grants them — so there is no read that would benefit from deferring
     * this, and the repository join-fetches the whole shelf in one query anyway.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "cosmetic_bundle_item",
            joinColumns = @JoinColumn(name = "bundle_id"),
            inverseJoinColumns = @JoinColumn(name = "cosmetic_id"))
    @OrderBy("price ASC")
    private List<Cosmetic> items = new ArrayList<>();

    @Column(name = "created_date")
    private Instant createdDate;
}
