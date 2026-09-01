package com.gamebuddy.profile.interfaces.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * One offer on the bundle shelf.
 *
 * <p>Carries the parts in full rather than their ids: the shelf draws both artworks and
 * both names beside the price, and a client that had to resolve ids against the frames and
 * banners lists would be reassembling something the server already knows.
 */
@Getter
@Setter
public class BundleDto {

    private String id;

    private String name;

    /** What the set costs, in coins. */
    private int price;

    /**
     * What the parts cost bought separately.
     *
     * <p>Sent rather than left for the client to sum, because it is the number the saving
     * is quoted against and both sides must agree on it to the coin.
     */
    private int partsPrice;

    private List<CosmeticDto> items;

    /**
     * Whether this gamer already owns every part.
     *
     * <p>An owned bundle is not a thing that exists — see {@code CosmeticBundle} — so this
     * is derived from the parts. It exists to stop the shelf offering a set that would be
     * refused, which is the same job {@code owned} does on a single item.
     */
    private boolean owned;
}
