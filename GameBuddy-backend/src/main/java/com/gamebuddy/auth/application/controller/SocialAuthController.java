package com.gamebuddy.auth.application.controller;

import com.gamebuddy.auth.domain.service.SocialAuthService;
import com.gamebuddy.auth.interfaces.dto.LinkProvidersResponseBody;
import com.gamebuddy.auth.interfaces.dto.LinkStartResponseBody;
import com.gamebuddy.auth.interfaces.dto.SocialIdentitiesResponseBody;
import com.gamebuddy.auth.interfaces.dto.SocialSessionResponseBody;
import com.gamebuddy.auth.interfaces.request.SocialExchangeRequest;
import com.gamebuddy.auth.interfaces.request.SocialGoogleRequest;
import com.gamebuddy.auth.interfaces.response.LinkProvidersResponse;
import com.gamebuddy.auth.interfaces.response.LinkStartResponse;
import com.gamebuddy.auth.interfaces.response.SocialIdentitiesResponse;
import com.gamebuddy.auth.interfaces.response.SocialSessionResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
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
 * Signing in with Google or Discord.
 *
 * <p>Separate from {@link AuthController} because the responses are a different shape — one
 * of these answers a browser mid-redirect — and separate from {@link AccountLinkController}
 * because linking and signing in are different questions with different security properties,
 * even where they talk to the same company.
 *
 * <p><strong>Four public paths</strong>, listed one by one in {@code SecurityConfig} rather
 * than as {@code /auth/social/**}: this is where accounts are created, and a wildcard is one
 * refactor away from exposing something nobody meant to expose. Public is necessary — a
 * person signing in has no token yet, by definition — and each is throttled by the
 * {@code social} budget.
 */
@RestController
@RequestMapping("/auth/social")
@RequiredArgsConstructor
@Tag(name = "Social sign-in", description = "Google and Discord")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    /** Which providers this deployment can offer, so the app draws only those buttons. */
    @GetMapping("/providers")
    public ResponseEntity<LinkProvidersResponse> providers() {
        LinkProvidersResponse response = new LinkProvidersResponse();
        response.setBody(new BaseBody<>(new LinkProvidersResponseBody(socialAuthService.availableProviders())));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }

    /** The whole Google flow: one token in, one session out. */
    @PostMapping("/google")
    public ResponseEntity<SocialSessionResponse> google(@Valid @RequestBody SocialGoogleRequest request) {
        return ResponseEntity.ok(
                session(socialAuthService.signInWithGoogle(request.getIdToken(), request.getAcceptedTerms())));
    }

    /** Mints a ticket and hands back the Discord URL for the system browser. */
    @PostMapping("/discord/start")
    public ResponseEntity<LinkStartResponse> startDiscord() {
        LinkStartResponse response = new LinkStartResponse();
        response.setBody(new BaseBody<>(new LinkStartResponseBody(socialAuthService.startDiscordLogin())));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }

    /**
     * Discord's redirect.
     *
     * <p>{@code code} and {@code state} are optional at the signature because pressing Cancel
     * produces neither, and that is somebody changing their mind rather than a 400.
     */
    @GetMapping("/discord/callback")
    public ResponseEntity<Void> discordCallback(
            @RequestParam(required = false) String code, @RequestParam(required = false) String state) {
        String deepLink = socialAuthService.completeDiscordLogin(code, state);
        return ResponseEntity.status(302).location(URI.create(deepLink)).build();
    }

    /** Trades the ticket the callback handed the app for a session. */
    @PostMapping("/exchange")
    public ResponseEntity<SocialSessionResponse> exchange(@Valid @RequestBody SocialExchangeRequest request) {
        return ResponseEntity.ok(session(socialAuthService.exchange(request.getTicket(), request.getAcceptedTerms())));
    }

    /** What this account can sign in with. Authenticated: it is about the caller. */
    @GetMapping("/identities")
    public ResponseEntity<SocialIdentitiesResponse> identities(@AuthenticationPrincipal Gamer principal) {
        SocialIdentitiesResponseBody body = socialAuthService.identities(principal);
        SocialIdentitiesResponse response = new SocialIdentitiesResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<DefaultMessageResponse> unlink(
            @AuthenticationPrincipal Gamer principal, @PathVariable String provider) {
        return ResponseEntity.ok(socialAuthService.unlink(principal, provider));
    }

    private static SocialSessionResponse session(SocialSessionResponseBody body) {
        SocialSessionResponse response = new SocialSessionResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
