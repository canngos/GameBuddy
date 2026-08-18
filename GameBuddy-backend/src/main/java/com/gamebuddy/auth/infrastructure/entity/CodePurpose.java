package com.gamebuddy.auth.infrastructure.entity;

/**
 * What a six-digit code may be redeemed for.
 *
 * <p>This exists because it did not. One code type served both flows, distinguished only by
 * the wording of the email it arrived in — so a code mailed as "GameBuddy - Password Reset"
 * was perfectly redeemable at {@code POST /auth/verify}, which marks the account verified,
 * revokes its tokens and hands back a session. Anyone who could read the mailbox could sign
 * in without ever knowing or changing the password, and the account owner would see only
 * that they had been signed out.
 *
 * <p>Scoping is one column and one comparison, and it makes each flow able to redeem only
 * what it asked for.
 */
public enum CodePurpose {

    /** Confirming an address at signup. Redeemable at {@code /auth/verify}. */
    REGISTRATION,

    /** Proving mailbox control before a password reset. Redeemable at {@code /auth/reset/verify}. */
    PASSWORD_RESET
}
