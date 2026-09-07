package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What a gamer holds after buying a consumable.
 *
 * <p>The balance comes back with it so the Market header does not have to be refetched, and
 * so the client never infers a new balance by subtracting — a number it would get wrong the
 * moment two purchases overlap.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConsumableResponseBody implements BaseModel {

    private int coinBalance;

    /** Super likes owned. These do not expire. */
    private int superLikes;

    /** Extra likes bought for today only, on top of the tier's cap. */
    private int bonusAccepts;
}
