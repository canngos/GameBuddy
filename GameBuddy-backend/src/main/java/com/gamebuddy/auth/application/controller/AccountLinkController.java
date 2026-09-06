package com.gamebuddy.auth.application.controller;

import com.gamebuddy.auth.domain.service.AccountLinkService;
import com.gamebuddy.auth.interfaces.request.LinkVisibilityRequest;
import com.gamebuddy.auth.interfaces.response.LinkProvidersResponse;
import com.gamebuddy.auth.interfaces.response.LinkStartResponse;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Linking a Discord account, and the callback that finishes it.
 *
 * <p>Under {@code /auth} with the rest of the account-settings endpoints, but its own
 * controller: one of these is answered to a browser mid-redirect rather than to the app,
 * and mixing the two response styles in {@code AuthController} would invite somebody to
 * copy the wrong one.
 *
 * <p><strong>The callback is a public path</strong> — see {@code SecurityConfig}. It has to
 * be: the caller is a browser coming back from Discord with no token to present. It is not
 * unauthenticated, though. It carries a single-use link ticket that is bound server-side to
 * the account that started the flow, and that ticket is the whole security boundary for
 * this feature, exactly as the shared secret is for the RevenueCat webhook and
 * the signature is for the AdMob callback.
 */
@RestController
@RequestMapping("/auth/link")
@RequiredArgsConstructor
@Tag(name = "Account linking", description = "Verified Discord accounts")
public class AccountLinkController {

    private final AccountLinkService accountLinkService;

    /**
     * Which providers this deployment can offer.
     *
     * <p>Read before the screen draws its rows. Authenticated like the rest — it says nothing
     * about the caller, but there is no reason for it to answer anonymously either.
     */
    @GetMapping("/providers")
    public ResponseEntity<LinkProvidersResponse> providers() {
        return ResponseEntity.ok(accountLinkService.availableProviders());
    }

    /**
     * Mints a ticket and hands back the provider URL to open.
     *
     * <p>Authenticated, unlike the callback. It is where the account is established; the
     * callback learns it from the ticket this issues, so the JWT never has to travel
     * through a browser redirect.
     */
    @PostMapping("/{provider}/start")
    public ResponseEntity<LinkStartResponse> start(
            @AuthenticationPrincipal Gamer principal, @PathVariable String provider) {
        return ResponseEntity.ok(accountLinkService.startLink(principal, provider));
    }

    /**
     * Discord's redirect.
     *
     * <p>{@code code} and {@code state} are both optional at the signature, because the user
     * pressing Cancel produces neither and that is not an error worth a 400 — it is somebody
     * changing their mind, and they should land back in the app like everybody else.
     */
    @GetMapping("/discord/callback")
    public ResponseEntity<Void> discordCallback(
            @RequestParam(required = false) String code, @RequestParam(required = false) String state) {
        return redirectToApp(accountLinkService.completeDiscordLink(code, state));
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<DefaultMessageResponse> unlink(
            @AuthenticationPrincipal Gamer principal, @PathVariable String provider) {
        return ResponseEntity.ok(accountLinkService.unlink(principal, provider));
    }

    @PutMapping("/{provider}/visibility")
    public ResponseEntity<DefaultMessageResponse> setVisibility(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String provider,
            @Valid @RequestBody LinkVisibilityRequest request) {
        return ResponseEntity.ok(accountLinkService.setVisibility(principal, provider, request));
    }

    /**
     * Sends the browser back into the app.
     *
     * <p>A 302 to a {@code gamebuddy://} URL, which Android hands to the app because the
     * scheme is registered. Success and failure both come back this way — the status rides in
     * the query string and the app explains it, because a person who has just been bounced
     * through two websites should end up looking at the screen they started from, not at a
     * browser tab showing an error document.
     */
    private ResponseEntity<Void> redirectToApp(String deepLink) {
        return ResponseEntity.status(302).location(URI.create(deepLink)).build();
    }
}
