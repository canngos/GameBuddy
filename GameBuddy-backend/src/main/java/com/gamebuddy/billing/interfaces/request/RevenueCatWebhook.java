package com.gamebuddy.billing.interfaces.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * One event as RevenueCat posts it.
 *
 * <p>The payload nests the event under an {@code event} key, alongside an
 * {@code api_version} we do not read. Unknown fields are ignored on purpose: RevenueCat
 * adds them, and a webhook that starts 400-ing because a field appeared would silently
 * stop granting entitlements to people who have paid.
 *
 * @param apiVersion RevenueCat's payload version, carried for logging only
 * @param event the event itself
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevenueCatWebhook(@JsonProperty("api_version") String apiVersion, Event event) {

    /**
     * @param id stable per event and repeated on every retry, so this is what makes us idempotent
     * @param appUserId whoever the purchase belongs to — our {@code gamer.user_id}, and only that
     *     because the app calls {@code Purchases.logIn(userId)} before buying. Arriving as a
     *     RevenueCat anonymous id ({@code $RCAnonymousID:…}) means the app failed to identify the
     *     user and the entitlement has nowhere to go.
     * @param expirationAtMs null for a consumable, which never expires
     * @param store APP_STORE, PLAY_STORE, STRIPE, AMAZON, PROMOTIONAL…
     * @param cancelReason UNSUBSCRIBE, BILLING_ERROR, CUSTOMER_SUPPORT… only set on a cancellation
     * @param periodType TRIAL, INTRO, NORMAL, PROMOTIONAL, PREPAID
     * @param transferredFrom only on TRANSFER: the ids losing the entitlement
     * @param transferredTo only on TRANSFER: the ids gaining it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            String id,
            String type,
            @JsonProperty("app_user_id") String appUserId,
            @JsonProperty("product_id") String productId,
            @JsonProperty("purchased_at_ms") Long purchasedAtMs,
            @JsonProperty("expiration_at_ms") Long expirationAtMs,
            String store,
            @JsonProperty("transaction_id") String transactionId,
            @JsonProperty("original_transaction_id") String originalTransactionId,
            @JsonProperty("entitlement_ids") List<String> entitlementIds,
            @JsonProperty("cancel_reason") String cancelReason,
            @JsonProperty("period_type") String periodType,
            @JsonProperty("transferred_from") List<String> transferredFrom,
            @JsonProperty("transferred_to") List<String> transferredTo) {

        public Instant purchasedAt() {
            return purchasedAtMs == null ? null : Instant.ofEpochMilli(purchasedAtMs);
        }

        public Instant expiresAt() {
            return expirationAtMs == null ? null : Instant.ofEpochMilli(expirationAtMs);
        }
    }
}
