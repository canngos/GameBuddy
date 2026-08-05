package com.gamebuddy.billing.infrastructure.entity;

/** Which store processed the payment. Determines how the receipt is verified. */
public enum PurchasePlatform {
    APPLE_APP_STORE,
    GOOGLE_PLAY
}
