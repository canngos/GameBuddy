package com.gamebuddy.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * How a random token is stored: as a digest, never as itself.
 *
 * <p>Sessions, password-reset tickets and account-link tickets all do the same thing —
 * hand out 32 random bytes and keep only their SHA-256 — and each had written the six
 * lines out again. One copy, so a change of mind about the algorithm is a change in one
 * place rather than a hunt.
 *
 * <p><strong>Only for high-entropy values.</strong> A fast digest is right here and wrong
 * next door: the six-digit verification codes are bcrypt-hashed, because a million
 * possibilities fall to a GPU in seconds and bcrypt's cost is the only thing standing in
 * the way. Thirty-two random bytes have nothing to protect against exhaustion. Passing a
 * password or a short code to this method would be a serious mistake.
 */
public final class TokenHashing {

    private TokenHashing() {}

    /** @return lowercase hex of the SHA-256 of {@code token} */
    public static String sha256Hex(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
