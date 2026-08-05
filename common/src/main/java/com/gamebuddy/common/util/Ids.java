package com.gamebuddy.common.util;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import java.util.UUID;

/**
 * Identifier parsing that fails as a 400 rather than a 500.
 *
 * <p>Every service reached straight for {@code UUID.fromString(pathVariable)}, so any
 * client sending a malformed id got an HTTP 500 and a stack trace in the logs. The same
 * helper now serves all of them.
 */
public final class Ids {

    private Ids() {}

    public static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "malformed identifier", e);
        }
    }
}
