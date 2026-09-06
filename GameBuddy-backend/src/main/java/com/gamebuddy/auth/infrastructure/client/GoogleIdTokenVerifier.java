package com.gamebuddy.auth.infrastructure.client;

/**
 * Turns a Google ID token into the three facts a sign-in needs.
 *
 * <p>An interface with one implementation, for two reasons that are both about testing. The
 * service that decides who an identity becomes is where the interesting rules live —
 * attaching to an existing account, refusing an unverified address, requiring terms — and
 * none of them should need an RSA key pair to exercise. And the functional suite points this
 * at a stub issuer rather than at Google, which is only possible if verification is a seam.
 */
public interface GoogleIdTokenVerifier {

    /**
     * Verifies signature, issuer, audience and expiry, and reads the claims.
     *
     * @return the verified identity
     * @throws com.gamebuddy.common.exception.BusinessException {@code SOCIAL_TOKEN_INVALID}
     *     for every way a token can fail to check out. The distinctions are logged, never
     *     returned: telling whoever holds a forged token which check refused them is free
     *     help for them.
     */
    GoogleIdentity verify(String idToken);

    /**
     * A verified Google account.
     *
     * @param subject Google's {@code sub}. Stable per account per OAuth project, and the
     *     only field an identity is ever matched on.
     * @param email the address on the account, which people do change
     * @param emailVerified whether Google vouches for that address. A false here is what
     *     stops an unverified address being used to claim an existing GameBuddy account.
     * @param name the display name, used only to suggest a username
     */
    record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {}
}
