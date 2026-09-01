package com.gamebuddy.profile.application.controller;

import com.gamebuddy.profile.domain.service.CosmeticService;
import com.gamebuddy.profile.interfaces.response.CosmeticsResponse;
import com.gamebuddy.shared.entity.CosmeticKind;
import com.gamebuddy.shared.entity.Gamer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The cosmetics store: frames and banners.
 *
 * <p>Separate from {@code ProfileController}, which already carries the profile, the
 * catalogue, achievements, friends and avatar uploads. Every endpoint here returns the same
 * store body, so they belong together and nowhere else.
 */
@RestController
@RequestMapping("/application/cosmetics")
@RequiredArgsConstructor
public class CosmeticController {

    private final CosmeticService cosmeticService;

    /** The store, with what this gamer owns and wears already marked. */
    @GetMapping
    public ResponseEntity<CosmeticsResponse> getCosmetics(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(cosmeticService.getCosmetics(principal));
    }

    @PostMapping("/{cosmeticId}/buy")
    public ResponseEntity<CosmeticsResponse> buy(
            @AuthenticationPrincipal Gamer principal, @PathVariable String cosmeticId) {
        return ResponseEntity.ok(cosmeticService.buy(principal, cosmeticId));
    }

    /**
     * Buys a whole set. Its own path segment rather than a flag on {@code buy}, because a
     * bundle id and a cosmetic id name different things and a caller that confused them
     * should get a 404 rather than a surprise.
     */
    @PostMapping("/bundles/{bundleId}/buy")
    public ResponseEntity<CosmeticsResponse> buyBundle(
            @AuthenticationPrincipal Gamer principal, @PathVariable String bundleId) {
        return ResponseEntity.ok(cosmeticService.buyBundle(principal, bundleId));
    }

    @PostMapping("/{cosmeticId}/equip")
    public ResponseEntity<CosmeticsResponse> equip(
            @AuthenticationPrincipal Gamer principal, @PathVariable String cosmeticId) {
        return ResponseEntity.ok(cosmeticService.equip(principal, cosmeticId));
    }

    /**
     * Takes off whatever is in one slot.
     *
     * <p>Keyed by kind rather than by the item's id, because "remove my frame" is what the
     * gamer means and it does not require the client to know what is currently on.
     */
    @DeleteMapping("/equipped/{kind}")
    public ResponseEntity<CosmeticsResponse> unequip(
            @AuthenticationPrincipal Gamer principal, @PathVariable CosmeticKind kind) {
        return ResponseEntity.ok(cosmeticService.unequip(principal, kind));
    }
}
