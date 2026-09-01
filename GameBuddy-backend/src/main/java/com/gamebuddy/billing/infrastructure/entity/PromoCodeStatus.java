package com.gamebuddy.billing.infrastructure.entity;

/**
 * Why a code can or cannot be redeemed right now, as one word for the console.
 *
 * <p>Derived on read rather than stored. Two of these arrive on their own — a code expires
 * because time passed, and exhausts because somebody else redeemed it — so a stored column
 * would be wrong between the moment it changed and the next time anything wrote the row.
 */
public enum PromoCodeStatus {

    /** Redeemable: switched on, inside its window, and with room left. */
    ACTIVE,

    /** The validity window closed. */
    EXPIRED,

    /** Every redemption the code allowed has been taken. */
    EXHAUSTED,

    /** An administrator switched it off. Reversible; the rows are all still here. */
    DISABLED
}
