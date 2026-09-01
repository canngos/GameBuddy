package com.gamebuddy.profile.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CosmeticDto {

    private String id;

    /** {@code FRAME}, {@code BANNER} or {@code THEME}. Sent as a string; the client switches on it. */
    private String kind;

    private String name;

    /** A resolved URL, not the stored object key. Null for a theme, which has no image. */
    private String image;

    /**
     * A theme's slug, and null for every other kind.
     *
     * <p>The client maps it to the pair of colours it draws — see
     * {@link com.gamebuddy.shared.entity.CosmeticKind#THEME}.
     */
    private String theme;

    private boolean animated;

    /** In coins. Zero means free to everyone. */
    private int price;

    /**
     * Whether this gamer can wear it.
     *
     * <p>Free items are owned by everyone, so this is true for them without any purchase
     * record existing. The client should gate the "Equip" button on this rather than on
     * {@code price == 0}, so that the rule lives in one place.
     */
    private boolean owned;

    /** Whether this is what the gamer is currently wearing in this slot. */
    private boolean equipped;

    /**
     * Whether it comes with membership rather than being for sale.
     *
     * <p>Sent because the price alone cannot express it: a membership item is stored at
     * zero, which is indistinguishable from a free one. Without this the client renders a
     * Buy button on something {@code DefaultCosmeticService.buy} refuses outright — an
     * affordance that can only ever fail.
     */
    private boolean membershipOnly;
}
