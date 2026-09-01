package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * The whole store, from the asking gamer's point of view.
 *
 * <p>Split by kind here rather than sent as one list, because the client renders two
 * sections and would otherwise partition it on arrival — the same work, done later, in a
 * language with worse tools for it.
 *
 * <p>Includes the coin balance so that buying does not need a second request to show the
 * new total. Buying and equipping both return this same body for the same reason: after a
 * purchase, {@code owned}, {@code equipped} and {@code coins} have all changed, and a
 * client that has to refetch to learn that will briefly render a store that disagrees with
 * itself.
 */
@Getter
@Setter
public class CosmeticsResponseBody implements BaseModel {

    private List<CosmeticDto> frames;
    private List<CosmeticDto> banners;

    /**
     * Card themes: colour, not artwork.
     *
     * <p>Each carries a slug in place of an image — see
     * {@link com.gamebuddy.shared.entity.CosmeticKind#THEME}.
     */
    private List<CosmeticDto> themes;

    /** Sets sold at a discount. Their parts also appear in the lists above. */
    private List<BundleDto> bundles;

    /** The gamer's balance after whatever this call did. */
    private int coins;
}
