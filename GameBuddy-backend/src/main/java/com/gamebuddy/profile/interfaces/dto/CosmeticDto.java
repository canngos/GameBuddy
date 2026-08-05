package com.gamebuddy.profile.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CosmeticDto {

    private String id;

    /** {@code FRAME} or {@code BANNER}. Sent as a string; the client switches on it. */
    private String kind;

    private String name;

    /** A resolved URL, not the stored object key. */
    private String image;

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
}
