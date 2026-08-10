package com.gamebuddy.match.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.match.domain.service.FeedFilters;
import com.gamebuddy.match.domain.service.MatchService;
import com.gamebuddy.match.interfaces.request.GamerRequest;
import com.gamebuddy.match.interfaces.response.AcceptResponse;
import com.gamebuddy.match.interfaces.response.LikedYouResponse;
import com.gamebuddy.match.interfaces.response.RecommendationResponse;
import com.gamebuddy.match.interfaces.response.SwipeAllowanceResponse;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The authenticated principal arrives via {@link AuthenticationPrincipal} rather than as a
 * header the controller re-parses with {@code token.substring(7)}.
 */
@RestController
@RequestMapping("/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /**
     * The deck.
     *
     * <p>Every filter is optional, and sending none is the free behaviour. Supplying any
     * of them is a Gold entitlement and is refused with 402 otherwise — the client already
     * routes that to the paywall.
     */
    @GetMapping("/get/recommendations")
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @AuthenticationPrincipal Gamer principal,
            @RequestParam(required = false) String gameId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Boolean onlineNow) {
        return ResponseEntity.ok(
                matchService.getRecommendations(principal, new FeedFilters(gameId, country, onlineNow)));
    }

    @GetMapping("/get/selected/game/{gameId}")
    public ResponseEntity<RecommendationResponse> getSelectedGameRecommendations(
            @AuthenticationPrincipal Gamer principal, @PathVariable String gameId) {
        return ResponseEntity.ok(matchService.getSelectedGameRecommendations(principal, gameId));
    }

    /** Everyone this gamer has mutually matched with; these are the chattable people. */
    @GetMapping("/get/matches")
    public ResponseEntity<RecommendationResponse> getMatches(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(matchService.getMatches(principal));
    }

    @PostMapping("/accept")
    public ResponseEntity<AcceptResponse> acceptMatch(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody GamerRequest gamerRequest) {
        return ResponseEntity.ok(matchService.acceptGamer(principal, gamerRequest));
    }

    @PostMapping("/decline")
    public ResponseEntity<DefaultMessageResponse> declineMatch(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody GamerRequest gamerRequest) {
        return ResponseEntity.ok(matchService.declineGamer(principal, gamerRequest));
    }

    /**
     * Who has liked this gamer.
     *
     * <p>Always returns the count; returns the identities only on a tier that includes
     * them, with {@code locked} telling the client which it got.
     */
    @GetMapping("/get/liked-you")
    public ResponseEntity<LikedYouResponse> getWhoLikedYou(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(matchService.getWhoLikedYou(principal));
    }

    /**
     * Accepts left today. Declining is never rationed, so it is not reported here.
     *
     * <p>Separate from the feed so the client can show the allowance up front. Being
     * refused mid-session without warning turns a limit into a cancellation rather than an
     * upgrade.
     */
    @GetMapping("/get/accept-allowance")
    public ResponseEntity<SwipeAllowanceResponse> getSwipeAllowance(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(matchService.getSwipeAllowance(principal));
    }
}
