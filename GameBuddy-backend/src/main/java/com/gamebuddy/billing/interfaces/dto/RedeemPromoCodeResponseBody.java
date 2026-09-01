package com.gamebuddy.billing.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What a redemption actually did.
 *
 * <p>The resulting balance and expiry are sent, not just what the code promised, so the
 * screen can say "you now have 1,340" rather than "500 added" and be right about it. The
 * client holds a cached balance that may be a few minutes old; adding to that locally is
 * how a number ends up wrong on screen in the one moment somebody is watching it.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RedeemPromoCodeResponseBody implements BaseModel {

    /** COIN or GOLD. */
    private String kind;

    /** What the code was worth, for the success message. */
    private Integer coinAmount;

    private Integer goldDays;

    /** The balance after crediting. */
    private int coinBalance;

    /** When Gold now runs out. Null for a coin code on an account with no membership. */
    private Instant goldExpiresAt;
}
