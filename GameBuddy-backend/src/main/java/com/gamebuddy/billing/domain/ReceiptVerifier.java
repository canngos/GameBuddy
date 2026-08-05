package com.gamebuddy.billing.domain;

import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import java.time.Instant;

/**
 * Checks with the store that a purchase really happened.
 *
 * <p><strong>This is the boundary that decides whether the app gives away paid goods.</strong>
 * The client says "I bought Gold"; a client can say anything. Only Apple and Google know
 * whether money moved, so nothing may be granted on the strength of the request alone —
 * the receipt has to be presented back to the issuing store and the answer believed
 * instead.
 *
 * <p>Implementations must verify, at minimum:
 * <ul>
 *   <li>the receipt's signature or the store's direct response, over TLS to the store's own
 *       host — not to a URL supplied in the request;
 *   <li>that the bundle/package id is ours, so a receipt from another app cannot be
 *       replayed here;
 *   <li>that the product id in the receipt matches the one being claimed, so a coin pack
 *       cannot be redeemed as a subscription;
 *   <li>that the transaction has not been refunded or revoked.
 * </ul>
 */
public interface ReceiptVerifier {

    /** Which store this implementation speaks to. */
    PurchasePlatform platform();

    /**
     * @param receipt the opaque payload from the client
     * @param claimedProduct what the client says it bought — believed only if the store agrees
     * @return the verified purchase
     * @throws com.gamebuddy.common.exception.BusinessException with
     *     {@code PURCHASE_VERIFICATION_FAILED} if the store does not confirm it
     */
    VerifiedPurchase verify(String receipt, Product claimedProduct);

    /**
     * What the store confirmed.
     *
     * @param storeTransactionId the store's own id, used to make redemption idempotent
     * @param product the product the store says was bought
     * @param purchasedAt when the store recorded the payment
     * @param expiresAt subscription expiry as the store reports it, or null for consumables
     */
    record VerifiedPurchase(String storeTransactionId, Product product, Instant purchasedAt, Instant expiresAt) {}
}
