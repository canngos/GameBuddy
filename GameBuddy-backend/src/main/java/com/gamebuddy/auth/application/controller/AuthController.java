package com.gamebuddy.auth.application.controller;

import com.gamebuddy.auth.domain.service.AuthService;
import com.gamebuddy.auth.interfaces.request.*;
import com.gamebuddy.auth.interfaces.response.*;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated endpoints receive the principal via {@link AuthenticationPrincipal}
 * instead of re-reading the {@code Authorization} header and calling
 * {@code token.substring(7)}, which produced an unauthenticated HTTP 500 for any
 * header shorter than seven characters.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, verification and account settings")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return new ResponseEntity<>(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verifyCode(@Valid @RequestBody VerifyRequest request) {
        return ResponseEntity.ok(authService.verifyCode(request));
    }

    @PostMapping("/sendCode")
    public ResponseEntity<DefaultMessageResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return ResponseEntity.ok(authService.sendVerificationEmail(request));
    }

    /**
     * Step one of a forgotten-password reset: spend the mailed code for a short-lived ticket.
     *
     * <p>The code itself is requested through {@code /auth/sendCode} with
     * {@code isRegister=false} — that endpoint is already throttled and already answers
     * identically whether or not the address has an account, so there is nothing to add here.
     */
    @PostMapping("/reset/verify")
    public ResponseEntity<ResetVerifyResponse> verifyResetCode(@Valid @RequestBody ResetVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyResetCode(request));
    }

    /** Step two: the ticket from step one, plus the password to set. */
    @PostMapping("/reset/pwd")
    public ResponseEntity<DefaultMessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    /**
     * Extends the caller's session, returning a token with a later expiry.
     *
     * <p>Header-based for the same reason {@code validateToken} is: the session's start
     * is a claim inside the presented token, and the principal alone does not carry it.
     * The filter has already verified the token by the time this runs — an expired one
     * never reaches here, it is refused as 401 upstream, which is the correct answer to
     * "refresh a session that is already over".
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @AuthenticationPrincipal Gamer principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(authService.refreshSession(principal, BearerToken.require(authorization)));
    }

    /** Kept header-based: this endpoint's whole job is to inspect a supplied token. */
    @PostMapping("/validateToken")
    public ResponseEntity<TokenResponse> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(authService.validateToken(BearerToken.require(authorization)));
    }

    @PostMapping("/username")
    public ResponseEntity<DefaultMessageResponse> setUsername(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody UsernameRequest request) {
        return ResponseEntity.ok(authService.setUsername(principal, request));
    }

    @PostMapping("/details")
    public ResponseEntity<DefaultMessageResponse> details(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody DetailsRequest request) {
        return ResponseEntity.ok(authService.details(principal, request));
    }

    @PutMapping("/change/pwd")
    public ResponseEntity<DefaultMessageResponse> changePwd(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody ChangePwdRequest request) {
        return ResponseEntity.ok(authService.changePwd(principal, request));
    }

    @PutMapping("/change/avatar")
    public ResponseEntity<DefaultMessageResponse> changeAvatar(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody ChangeAvatarRequest request) {
        return ResponseEntity.ok(authService.changeAvatar(principal, request));
    }

    @PutMapping("/change/age")
    public ResponseEntity<DefaultMessageResponse> changeAge(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody ChangeAgeRequest request) {
        return ResponseEntity.ok(authService.changeAge(principal, request));
    }

    /**
     * Deletes the caller's account.
     *
     * <p>Requires the password: a stolen token must not be enough to destroy the account. An
     * account signed up through Google or Discord has none, so the token is passed through as
     * well and its age stands in — see {@code DefaultAuthService#requireFreshSession}.
     */
    @DeleteMapping("/account")
    public ResponseEntity<DefaultMessageResponse> deleteAccount(
            @AuthenticationPrincipal Gamer principal,
            @Valid @RequestBody DeleteAccountRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(authService.deleteAccount(principal, request, BearerToken.require(authorization)));
    }

    /**
     * Sets a first password on an account that has none.
     *
     * <p>Its own endpoint rather than a mode of {@code PUT /auth/change/pwd}: that one proves
     * the old password and ends every session, and neither is right here.
     */
    @PostMapping("/password")
    public ResponseEntity<DefaultMessageResponse> setPassword(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody SetPasswordRequest request) {
        return ResponseEntity.ok(authService.setPassword(principal, request));
    }

    /** Called by the client on every start; Firebase rotates device tokens. */
    @PutMapping("/fcm-token")
    public ResponseEntity<DefaultMessageResponse> updateFcmToken(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody FcmTokenRequest request) {
        return ResponseEntity.ok(authService.updateFcmToken(principal, request));
    }

    @PutMapping("/change/games")
    public ResponseEntity<DefaultMessageResponse> changeGames(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody ChangeDetailRequest request) {
        return ResponseEntity.ok(authService.changeGames(principal, request));
    }

    @PutMapping("/change/keywords")
    public ResponseEntity<DefaultMessageResponse> changeKeywords(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody ChangeDetailRequest request) {
        return ResponseEntity.ok(authService.changeKeywords(principal, request));
    }

    /**
     * Reuses {@code ChangeDetailRequest} — the body is a list of names either way, and a
     * third single-field request class would differ from the other two only in what the
     * field is called.
     */
    @PutMapping("/change/platforms")
    public ResponseEntity<DefaultMessageResponse> changePlatforms(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody ChangeDetailRequest request) {
        return ResponseEntity.ok(authService.changePlatforms(principal, request));
    }
}
