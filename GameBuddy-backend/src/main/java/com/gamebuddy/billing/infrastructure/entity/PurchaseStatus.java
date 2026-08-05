package com.gamebuddy.billing.infrastructure.entity;

/** Where a purchase sits in its lifecycle. */
public enum PurchaseStatus {

    /** Verified by the store and granted. */
    GRANTED,

    /**
     * Refunded or charged back.
     *
     * <p>Kept as a row rather than deleted: the entitlement has to be revoked, and the
     * transaction id must stay claimed so the same receipt cannot simply be redeemed again.
     */
    REFUNDED
}
