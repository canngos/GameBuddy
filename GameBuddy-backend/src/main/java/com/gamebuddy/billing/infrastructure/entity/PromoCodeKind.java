package com.gamebuddy.billing.infrastructure.entity;

/**
 * What a promotion code hands over.
 *
 * <p>Two kinds rather than a reference to {@code Product}. The catalogue exists to match
 * what the stores sell — three coin packs and three subscription periods at fixed sizes —
 * and a promotion is not a sale: "300 coins" or "ten days of Gold" are perfectly ordinary
 * things to give away and neither is a product anyone can buy. Tying codes to the
 * catalogue would have made the giveaway inherit the price list's shape for no reason.
 */
public enum PromoCodeKind {

    /** Coins, credited to the balance through the ledger. */
    COIN,

    /** Gold membership, added to whatever the account already has. */
    GOLD
}
