package com.gamebuddy.auth.application.controller;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;

/**
 * Safe replacement for the {@code token.substring(7)} that appeared in every
 * controller method. A header of "abc", or a missing header, previously raised
 * {@code StringIndexOutOfBoundsException} and surfaced as an HTTP 500.
 */
final class BearerToken {

    private static final String PREFIX = "Bearer ";

    private BearerToken() {}

    static String require(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIX)) {
            throw new BusinessException(TransactionCode.TOKEN_NOT_FOUND);
        }
        String token = authorizationHeader.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(TransactionCode.TOKEN_NOT_FOUND);
        }
        return token;
    }
}
