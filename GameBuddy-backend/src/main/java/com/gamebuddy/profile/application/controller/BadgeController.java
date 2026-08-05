package com.gamebuddy.profile.application.controller;

import com.gamebuddy.profile.domain.service.BadgeService;
import com.gamebuddy.profile.interfaces.request.ShowcaseRequest;
import com.gamebuddy.profile.interfaces.response.BadgesResponse;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Badges: the board, claiming a reward, and choosing what to show.
 *
 * <p>Its own controller rather than three more methods on {@link ProfileController}, which
 * already carries the catalogue, friends and avatar uploads. Every endpoint here answers
 * with the whole board for the reason the store does — after a claim, the balance and that
 * badge's state have both moved, and one response beats two round trips that disagree in
 * between.
 */
@RestController
@RequestMapping("/application/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    /** Every mission, with this gamer's progress. Opening this is what awards them. */
    @GetMapping
    public ResponseEntity<BadgesResponse> getBadges(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(badgeService.getBadges(principal));
    }

    /** Claims the coins for an earned badge. */
    @PostMapping("/{code}/collect")
    public ResponseEntity<BadgesResponse> collect(@AuthenticationPrincipal Gamer principal, @PathVariable String code) {
        return ResponseEntity.ok(badgeService.collect(principal, code));
    }

    /**
     * Sets the three badges shown on the profile, in order.
     *
     * <p>PUT, not POST: it replaces the whole selection, and sending the same list twice
     * has to leave the same result.
     */
    @PutMapping("/showcase")
    public ResponseEntity<BadgesResponse> showcase(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody ShowcaseRequest request) {
        return ResponseEntity.ok(badgeService.showcase(principal, request.getCodes()));
    }
}
