package com.gamebuddy.profile.domain.service;

import com.gamebuddy.profile.interfaces.response.CosmeticsResponse;
import com.gamebuddy.shared.entity.CosmeticKind;
import com.gamebuddy.shared.entity.Gamer;

/** Browsing, buying and wearing frames and banners. */
public interface CosmeticService {

    /** The whole store, with {@code owned} and {@code equipped} filled in for this gamer. */
    CosmeticsResponse getCosmetics(Gamer principal);

    /**
     * Spends coins on a cosmetic.
     *
     * <p>Returns the refreshed store rather than a bare acknowledgement, because after a
     * purchase the balance, the owned flag and possibly an achievement have all changed.
     */
    CosmeticsResponse buy(Gamer principal, String cosmeticId);

    /**
     * Spends coins on a whole set at the set's price.
     *
     * <p>Refuses outright if any part is already owned — see the implementation for why
     * that is better than a partial grant or a prorated price.
     */
    CosmeticsResponse buyBundle(Gamer principal, String bundleId);

    /** Wears one. Replaces whatever occupied that slot. */
    CosmeticsResponse equip(Gamer principal, String cosmeticId);

    /** Takes off whatever is in the given slot. Wearing nothing is a valid look. */
    CosmeticsResponse unequip(Gamer principal, CosmeticKind kind);
}
