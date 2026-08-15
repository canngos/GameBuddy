package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.interfaces.request.*;
import com.gamebuddy.auth.interfaces.response.*;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;

/**
 * Authenticated operations take the resolved {@link Gamer} principal rather than a raw
 * {@code Authorization} header string.
 *
 * <p>The old signatures accepted the header and each controller passed
 * {@code token.substring(7)}, which threw {@code StringIndexOutOfBoundsException} —
 * an unauthenticated HTTP 500 — for any header shorter than seven characters, and
 * left every method re-parsing a token the security filter had already validated.
 */
public interface AuthService {

    // --- Unauthenticated ---------------------------------------------------

    LoginResponse login(LoginRequest loginRequest);

    RegisterResponse register(RegisterRequest registerRequest);

    VerifyResponse verifyCode(VerifyRequest verifyRequest);

    DefaultMessageResponse sendVerificationEmail(SendCodeRequest sendCodeRequest);

    TokenResponse validateToken(String token);

    /**
     * Extends the caller's session by issuing a fresh token for it.
     *
     * <p>What makes a session sliding: the token has a short life, and using the app
     * quietly renews it, so somebody who plays every week never meets a login screen
     * while an abandoned session still lapses about a week after it was last touched.
     *
     * <p>The session's own age is carried in the token and does not reset here, so this
     * cannot be used to hold a session open forever — past
     * {@code gamebuddy.jwt.max-session-age} the password is required again.
     *
     * @param bearerToken the caller's current token, which the filter has already
     *     verified; re-read here because the session's start is a claim inside it
     * @throws com.gamebuddy.common.exception.BusinessException
     *     {@code TOKEN_INVALID} when the session has outlived the ceiling, which the
     *     client treats exactly as it treats an expiry — by asking for the password
     */
    LoginResponse refreshSession(Gamer principal, String bearerToken);

    // --- Authenticated -----------------------------------------------------

    DefaultMessageResponse setUsername(Gamer principal, UsernameRequest usernameRequest);

    DefaultMessageResponse details(Gamer principal, DetailsRequest detailsRequest);

    DefaultMessageResponse changePwd(Gamer principal, ChangePwdRequest changePwdRequest);

    DefaultMessageResponse changeAvatar(Gamer principal, ChangeAvatarRequest changeAvatarRequest);

    DefaultMessageResponse changeAge(Gamer principal, ChangeAgeRequest changeAgeRequest);

    /**
     * Stores a refreshed Firebase device token.
     *
     * <p>Without this the token captured at registration is the only one the system ever
     * has, so push delivery ends silently the first time Firebase rotates it.
     */
    DefaultMessageResponse updateFcmToken(Gamer principal, FcmTokenRequest request);

    /**
     * Deletes the caller's account.
     *
     * <p>There was no way to do this at all. For a service holding an e-mail address, an
     * age, a country and private messages, erasure on request is an obligation under
     * GDPR and KVKK rather than a feature.
     */
    DefaultMessageResponse deleteAccount(Gamer principal, DeleteAccountRequest request);

    DefaultMessageResponse changeGames(Gamer principal, ChangeDetailRequest changeGamesRequest);

    DefaultMessageResponse changeKeywords(Gamer principal, ChangeDetailRequest changeKeywordsRequest);

    DefaultMessageResponse changePlatforms(Gamer principal, ChangeDetailRequest changePlatformsRequest);
}
