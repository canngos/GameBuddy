package com.gamebuddy.auth.domain.service;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What may be used as a username.
 *
 * <p>Previously the only constraint on the server was {@code @NotBlank}, and the real
 * rules lived in the app's {@code validation.ts}. A client is not a place to enforce
 * anything: anyone with curl could register a four-hundred-character name, a name made
 * of Cyrillic characters that render identically to Latin ones, or {@code moderator}.
 * The app keeps its copy so it can answer without a round trip — see the note at the top
 * of that file — but this is the authority.
 *
 * <p>The character set is deliberately narrow. Restricting to ASCII letters, digits and
 * underscore excludes every homoglyph attack in one stroke, which no blocklist of
 * confusable characters could do reliably. It also excludes people whose names are not
 * writable in ASCII, which is a real cost — but a username here is a handle shown to
 * strangers next to a photograph, not a legal name, and the display name is what a
 * profile shows.
 */
public final class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 20;

    private static final Pattern ALLOWED = Pattern.compile("^[a-zA-Z0-9_]+$");

    /**
     * Names that would let someone pass themselves off as the app or its staff. Compared
     * in lower case, so {@code MoDeRaToR} is caught too.
     *
     * <p>{@code moderator} is here for a second reason: it is the default username of the
     * one staff account — see {@code ModeratorBootstrap}. That account is created
     * directly rather than through this service, so it is not subject to this check.
     */
    private static final Set<String> RESERVED = Set.of(
            "admin",
            "administrator",
            "moderator",
            "mod",
            "staff",
            "support",
            "help",
            "system",
            "gamebuddy",
            "official",
            "root",
            "null",
            "undefined");

    /**
     * Deleting an account rewrites its username to {@code deleted_} plus the start of its
     * id, so the prefix has to be unusable by anyone else — otherwise a live account
     * could be made to look like the remains of a deleted one.
     */
    private static final String DELETED_PREFIX = "deleted_";

    private UsernamePolicy() {}

    /**
     * @param username already trimmed by the caller
     * @throws BusinessException if the name may not be used
     */
    public static void validate(String username) {
        if (username == null || username.isEmpty()) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "username cannot be empty");
        }
        if (username.length() < MIN_LENGTH || username.length() > MAX_LENGTH) {
            throw new BusinessException(
                    TransactionCode.INVALID_REQUEST,
                    "username must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(username).matches()) {
            throw new BusinessException(
                    TransactionCode.INVALID_REQUEST, "username may contain only letters, numbers and underscores");
        }
        // "___" passes the character check and reads as nothing at all.
        if (username.chars().noneMatch(Character::isLetterOrDigit)) {
            throw new BusinessException(
                    TransactionCode.INVALID_REQUEST, "username must contain at least one letter or number");
        }
        String lower = username.toLowerCase();
        if (RESERVED.contains(lower) || lower.startsWith(DELETED_PREFIX)) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "that username is not available");
        }
    }
}
