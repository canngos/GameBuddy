package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The swipe that was taken back.
 *
 * <p>The candidate is returned in full so the deck can put them straight back on top
 * without another round trip to the model — which would also record a second impression
 * for somebody who was already shown once, and quietly corrupt the training data with a
 * view that never happened.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RewindResponseBody implements BaseModel {

    /** Who came back. */
    private GamerDto gamer;

    /** Coins this cost. Zero on Gold, which gets rewinds as an entitlement. */
    private int coinsSpent;

    /** The balance afterwards, so the Market's header does not have to be refetched. */
    private int coinBalance;
}
