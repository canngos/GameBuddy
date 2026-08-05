package com.gamebuddy.auth.domain.service;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import java.util.Set;

/**
 * Minimum password requirements.
 *
 * <p>Previously the only constraint anywhere was {@code @NotBlank}, so "a" was a
 * perfectly acceptable password on both register and change-password.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    /** A small stop-list; not a substitute for a breach corpus, but catches the worst. */
    private static final Set<String> FORBIDDEN =
            Set.of("password", "12345678", "qwertyui", "gamebuddy", "11111111", "123456789");

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BusinessException(
                    TransactionCode.WEAK_PASSWORD,
                    "must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (FORBIDDEN.contains(password.toLowerCase())) {
            throw new BusinessException(TransactionCode.WEAK_PASSWORD, "this password is too common");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(
                    TransactionCode.WEAK_PASSWORD, "must contain at least one letter and one digit");
        }
    }
}
