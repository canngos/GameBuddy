package com.gamebuddy.auth.domain.service;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;

/**
 * The terms an account holder has to agree to, and which version of them.
 *
 * <p>Apple requires an app with user-generated content to publish terms containing no
 * tolerance for objectionable content or abusive users, and requires agreement to be an
 * act rather than an assumption — a checkbox that starts unticked, not a line of small
 * print under a button. {@code Gamer.termsAcceptedAt} and {@code Gamer.termsVersion}
 * record that act.
 *
 * <p>The version is a date. When the documents change materially, move it: existing
 * accounts then hold an acceptance of a superseded version, which is a fact worth being
 * able to see rather than one to overwrite. Nothing currently forces re-acceptance —
 * that is a product decision for the day it first matters, and the data needed to make
 * it is being kept from now on.
 *
 * <p>The documents themselves live in {@code documentation/legal/} and are served from
 * the marketing site, because a store listing needs a URL and an app needs a link.
 */
public final class TermsPolicy {

    /** Bump when the wording changes in a way an account holder would care about. */
    public static final String CURRENT_VERSION = "2026-08-20";

    private TermsPolicy() {}

    public static void requireAcceptance(Boolean accepted) {
        if (!Boolean.TRUE.equals(accepted)) {
            throw new BusinessException(TransactionCode.TERMS_NOT_ACCEPTED);
        }
    }
}
