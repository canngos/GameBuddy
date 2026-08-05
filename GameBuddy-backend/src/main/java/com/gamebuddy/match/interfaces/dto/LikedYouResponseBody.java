package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Who has liked this gamer, or how many have, depending on what they have paid for.
 *
 * <p>The count is deliberately free and the identities are not. Withholding the count as
 * well would leave nothing to upgrade <em>for</em> — "someone liked you" is the hook, and
 * "who?" is the product.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LikedYouResponseBody implements BaseModel {

    /** How many gamers have swiped yes without being answered. Always populated. */
    private int count;

    /** Empty for the free tier; see {@link #locked}. */
    private List<GamerDto> likedYou;

    /** True when {@link #likedYou} was withheld because the tier does not include it. */
    private boolean locked;
}
