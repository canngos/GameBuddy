package com.gamebuddy.billing.domain;

import com.gamebuddy.billing.domain.PurchaseService.VerifiedPurchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.billing.interfaces.request.RevenueCatWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Applies RevenueCat's account of what happened.
 *
 * <p>RevenueCat has already talked to Apple or Google and decided the purchase is real, so
 * this class does no verification — it maps an event onto a grant, an expiry, or a refund.
 *
 * <p><b>Unknown event types are ignored, not rejected.</b> RevenueCat adds event types over
 * time and sends every type the dashboard is configured for; answering an unrecognised one
 * with an error would make it retry forever and eventually disable the webhook, which would
 * take the recognised events down with it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueCatService {

    private final PurchaseService purchases;

    /** Handles one event. Returns quietly for anything that does not change entitlement. */
    public void handle(RevenueCatWebhook.Event event) {
        if (event == null || event.type() == null) {
            log.warn("Ignoring RevenueCat webhook with no event type");
            return;
        }

        switch (event.type()) {
            // Every one of these means "this account should have the product now".
            // PRODUCT_CHANGE is included because an upgrade or downgrade arrives as a new
            // transaction with a new expiry, and treating it as anything else would leave
            // somebody who upgraded on their old plan.
            case "INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION", "PRODUCT_CHANGE", "NON_RENEWING_PURCHASE" ->
                grant(event);

            // Access is already gone. RevenueCat sends this when the paid period actually
            // ended, which is the moment the entitlement stops — not CANCELLATION.
            case "EXPIRATION" -> purchases.expire(event.appUserId());

            case "CANCELLATION" -> cancellation(event);

            case "TRANSFER" -> transfer(event);

            // BILLING_ISSUE is a warning, not a revocation: the store is retrying the
            // charge and the subscriber keeps access through the grace period. Revoking
            // here would cut off people whose card simply needs updating, and EXPIRATION
            // arrives if the retries fail.
            case "BILLING_ISSUE", "SUBSCRIPTION_PAUSED", "TEST" -> log.info(
                    "RevenueCat {} for {} needs no entitlement change", event.type(), event.appUserId());

            default -> log.info("Ignoring unhandled RevenueCat event type {}", event.type());
        }
    }

    /**
     * A cancellation is not the loss of access.
     *
     * <p>It means auto-renew was switched off; the subscriber keeps what they paid for
     * until {@code EXPIRATION} arrives at the end of the period. Revoking here would take
     * away days somebody has already paid for, and would do it to the group most likely to
     * come back — people who cancelled but are still using it.
     *
     * <p>The exception is a refund, which RevenueCat reports as a cancellation with
     * {@code CUSTOMER_SUPPORT}. There the money has gone back, so the product has to as
     * well, immediately.
     */
    private void cancellation(RevenueCatWebhook.Event event) {
        if ("CUSTOMER_SUPPORT".equals(event.cancelReason())) {
            PurchasePlatform platform = platformOf(event.store());
            if (platform != null && event.transactionId() != null) {
                purchases.refund(platform, event.transactionId());
                return;
            }
            // No transaction to mark, but the money is still gone.
            purchases.expire(event.appUserId());
            return;
        }

        log.info(
                "{} cancelled auto-renew ({}); access continues until expiry",
                event.appUserId(),
                event.cancelReason());
    }

    private void grant(RevenueCatWebhook.Event event) {
        Product product = Product.byStoreId(event.productId()).orElse(null);
        if (product == null) {
            // Ours to fix, not RevenueCat's to retry: a product exists on the store that
            // this build does not know how to grant. Loud, because somebody has paid for
            // something they are not getting.
            log.error(
                    "RevenueCat reported a purchase of unknown product '{}' for {}; nothing granted",
                    event.productId(),
                    event.appUserId());
            return;
        }

        PurchasePlatform platform = platformOf(event.store());
        if (platform == null) {
            log.error(
                    "RevenueCat reported a purchase from unsupported store '{}' for {}; nothing granted",
                    event.store(),
                    event.appUserId());
            return;
        }

        if (event.appUserId() == null || event.appUserId().startsWith("$RCAnonymousID:")) {
            // The app never called Purchases.logIn, so RevenueCat does not know who this
            // is and neither do we. There is no account to grant to.
            log.error("RevenueCat purchase for anonymous user {}; nothing granted", event.appUserId());
            return;
        }

        // Falls back to the event id when the store gave no transaction id — true of some
        // promotional and non-consumable grants. The id is stable across retries, which is
        // all the idempotency key has to be.
        String transactionId = event.transactionId() != null ? event.transactionId() : event.id();

        purchases.grant(new VerifiedPurchase(
                event.appUserId(), product, platform, transactionId, event.purchasedAt(), event.expiresAt()));
    }

    private void transfer(RevenueCatWebhook.Event event) {
        if (event.transferredFrom() == null || event.transferredFrom().isEmpty()) {
            return;
        }
        // One purchase cannot entitle two accounts at once, so whoever had it loses it.
        // The gaining side is granted by the purchase event that accompanies the transfer.
        event.transferredFrom().forEach(from -> purchases.transfer(from, event.appUserId()));
    }

    /**
     * Maps RevenueCat's store names onto ours.
     *
     * <p>Only the two we ship on. STRIPE, AMAZON, MAC_APP_STORE and PROMOTIONAL return null
     * and are refused rather than guessed at — {@code purchase.platform} has a check
     * constraint allowing exactly two values, so inventing a third would fail at insert
     * time with a constraint violation instead of a clear message.
     */
    private PurchasePlatform platformOf(String store) {
        if (store == null) {
            return null;
        }
        return switch (store) {
            case "APP_STORE", "MAC_APP_STORE" -> PurchasePlatform.APPLE_APP_STORE;
            case "PLAY_STORE" -> PurchasePlatform.GOOGLE_PLAY;
            default -> null;
        };
    }
}
