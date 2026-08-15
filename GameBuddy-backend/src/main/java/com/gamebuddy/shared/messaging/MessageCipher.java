package com.gamebuddy.shared.messaging;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts message bodies before they reach the database.
 *
 * <p>The threat this addresses is the one that actually happens: a leaked backup, a stolen
 * disk, a database credential in the wrong hands. Any of those hands over every private
 * conversation on the platform. With this, they hand over ciphertext and the key is not in
 * the database to be taken with it.
 *
 * <p><strong>Not end-to-end.</strong> The server holds the key and can read messages. That
 * is a deliberate trade, not an oversight: GameBuddy pairs strangers and admits minors, and
 * a report has to be reviewable by a human. True end-to-end encryption would leave
 * moderators looking at ciphertext and make the reporting flow decorative. Say so in the
 * privacy policy rather than claiming more than is true.
 *
 * <p>AES-GCM, which authenticates as well as encrypts — a tampered row fails to decrypt
 * rather than yielding plausible garbage. Every message gets a fresh random nonce; reusing
 * one under the same key is the classic way to destroy GCM's guarantees entirely.
 */
@Slf4j
@Component
public class MessageCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    /** Bumped when the key changes; stored per row so old messages keep decrypting. */
    public static final short CURRENT_KEY_VERSION = 1;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MessageCipher(@Value("${gamebuddy.chat.encryption-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("gamebuddy.chat.encryption-key must be base64", e);
        }
        // Fail at startup rather than at the first message. A short key silently accepted
        // is a weak key used for the lifetime of the deployment.
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException("gamebuddy.chat.encryption-key must decode to 16, 24 or 32 bytes; got "
                    + raw.length + ". Generate one with: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /**
     * @return the ciphertext and the nonce it was produced with; both are needed to read it
     */
    public Encrypted encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new Encrypted(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), nonce);
        } catch (Exception e) {
            // Never log the plaintext, and never fall back to storing it unencrypted.
            log.error("Chat message could not be encrypted", e);
            throw new BusinessException(TransactionCode.DB_ERROR, e);
        }
    }

    /**
     * @param keyVersion which key the row was written under; only the current one is held
     *     today, so anything else is a row this deployment cannot read
     */
    public String decrypt(byte[] ciphertext, byte[] nonce, short keyVersion) {
        if (keyVersion != CURRENT_KEY_VERSION) {
            // Rotation is supported by the schema but not yet by this class: when a second
            // key exists, look it up here rather than failing.
            log.error("Chat message written under key version {}, which is not loaded", keyVersion);
            throw new BusinessException(TransactionCode.DB_ERROR);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Includes AEADBadTagException: the row was altered, or the wrong key is loaded.
            log.error("Chat message could not be decrypted", e);
            throw new BusinessException(TransactionCode.DB_ERROR, e);
        }
    }

    /**
     * @param ciphertext the encrypted body
     * @param nonce the per-message initialisation vector
     */
    public record Encrypted(byte[] ciphertext, byte[] nonce) {}
}
