package com.gamebuddy.billing.domain;

import com.gamebuddy.billing.infrastructure.entity.Purchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.billing.infrastructure.entity.PurchaseStatus;
import com.gamebuddy.billing.infrastructure.repository.PurchaseRepository;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a purchase that RevenueCat has already verified into an entitlement.
 *
 * <p><b>Nothing here verifies a receipt, and nothing here is reachable by a client.</b>
 * That is the point of the change: receipt verification is fiddly, security-critical, and
 * differs between Apple and Google, so it is delegated to RevenueCat, which talks to the
 * stores and tells us the outcome over a signed webhook. We never see a receipt.
 *
 * <p>The consequence worth being explicit about: the only remaining path into this class
 * is {@code RevenueCatService}, driven by an authenticated webhook. There is deliberately
 * no endpoint where the app can say "I bought this, please grant it" — that would be a
 * free subscription for anybody who can write a POST request, and removing verification
 * without removing that path is exactly how a paywall becomes decorative.
 *
 * <p>Two invariants survive from the old design and still matter:
 *
 * <ol>
 *   <li><b>Record the transaction, then grant.</b> The unique constraint on
 *       {@code (platform, store_transaction_id)} is what makes this idempotent, so it has
 *       to be taken before the entitlement is handed over. RevenueCat retries webhooks
 *       until we answer 2xx, so duplicates are the normal case, not an edge case.
 *   <li><b>Extend from whichever is later — the current expiry or now.</b> Only used when
 *       the store gives us no expiry of its own; when it does, the store wins, because it
 *       knows about grace periods, billing retries and refunds that we do not.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchases;
    private final GamerRepository gamers;
    private final Clock clock;

    /** What a verified purchase tells us, independent of who verified it. */
    public record VerifiedPurchase(
            String userId,
            Product product,
            PurchasePlatform platform,
            String storeTransactionId,
            Instant purchasedAt,
            Instant expiresAt) {}

    /**
     * Grants a purchase, or does nothing if it has already been granted.
     *
     * <p>Returns false for a duplicate rather than throwing. A webhook retry is not an
     * error — it is RevenueCat doing exactly what it promises — and answering it with a
     * failure would make it retry forever.
     *
     * @return true if this call is what granted the entitlement
     */
    @Transactional
    public boolean grant(VerifiedPurchase verified) {
        if (purchases.existsByPlatformAndStoreTransactionId(verified.platform(), verified.storeTransactionId())) {
            log.debug("Purchase {} already granted; ignoring replay", verified.storeTransactionId());
            return false;
        }

        Gamer gamer = gamers.findById(verified.userId()).orElse(null);
        if (gamer == null) {
            // Deliberately not an exception. A webhook for an account that no longer exists
            // (deleted between purchase and delivery) is not something a retry will fix, and
            // failing the request would have RevenueCat resend it indefinitely.
            log.warn("Purchase {} is for unknown account {}", verified.storeTransactionId(), verified.userId());
            return false;
        }

        Product product = verified.product();

        Purchase purchase = new Purchase();
        purchase.setId(UUID.randomUUID());
        purchase.setUserId(verified.userId());
        purchase.setProductId(product.storeId());
        purchase.setPlatform(verified.platform());
        purchase.setStoreTransactionId(verified.storeTransactionId());
        purchase.setStatus(PurchaseStatus.GRANTED);
        purchase.setPurchasedAt(verified.purchasedAt() == null ? clock.instant() : verified.purchasedAt());

        try {
            // Flushed here so a unique violation surfaces now, where it is a replay, rather
            // than escaping at commit time as a 500 that RevenueCat would retry.
            purchases.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent delivery of {} treated as a replay", verified.storeTransactionId());
            return false;
        }

        if (product.isSubscription()) {
            purchase.setEntitlementExpiresAt(grantSubscription(gamer, product, verified.expiresAt()));
        } else {
            gamer.setCoin(gamer.getCoin() + product.coins());
        }
        gamers.save(gamer);

        log.info("Granted {} to {} (transaction {})", product.storeId(), verified.userId(), verified.storeTransactionId());
        return true;
    }

    /**
     * Extends the paid period.
     *
     * @param storeExpiry the store's own expiry, authoritative when present
     */
    private Instant grantSubscription(Gamer gamer, Product product, Instant storeExpiry) {
        Instant now = clock.instant();

        Instant expiresAt;
        if (storeExpiry != null) {
            expiresAt = storeExpiry;
        } else {
            // Extend from the later of "now" and the existing expiry. Using the existing
            // expiry alone would back-date a renewal made after a lapse; using now alone
            // would throw away time an early renewer had already paid for.
            SubscriptionTier current =
                    SubscriptionTier.effective(gamer.getSubscriptionTier(), gamer.getSubscriptionExpiresAt(), now);
            Instant base = current == SubscriptionTier.BASIC || gamer.getSubscriptionExpiresAt() == null
                    ? now
                    : gamer.getSubscriptionExpiresAt();
            expiresAt = base.plus(product.period());
        }

        gamer.setSubscriptionTier(product.tier());
        gamer.setSubscriptionExpiresAt(expiresAt);
        return expiresAt;
    }

    /**
     * Ends a subscription now, without touching the purchase ledger.
     *
     * <p>For expiry: the paid period simply ran out. The row stays {@code GRANTED} because
     * it was — the gamer had every day they paid for.
     */
    @Transactional
    public void expire(String userId) {
        gamers.findById(userId).ifPresent(gamer -> {
            if (gamer.getSubscriptionExpiresAt() != null
                    && gamer.getSubscriptionExpiresAt().isAfter(clock.instant())) {
                gamer.setSubscriptionExpiresAt(clock.instant());
                gamers.save(gamer);
                log.info("Subscription for {} expired", userId);
            }
        });
    }

    /**
     * Revokes an entitlement after a refund or chargeback.
     *
     * <p>The row is kept and marked rather than deleted, so the transaction id stays
     * claimed and the same purchase cannot be delivered again.
     */
    @Transactional
    public void refund(PurchasePlatform platform, String storeTransactionId) {
        purchases.findByPlatformAndStoreTransactionId(platform, storeTransactionId).ifPresent(purchase -> {
            purchase.setStatus(PurchaseStatus.REFUNDED);

            gamers.findById(purchase.getUserId())
                    .ifPresent(gamer -> Product.byStoreId(purchase.getProductId()).ifPresent(product -> {
                        if (product.isSubscription()) {
                            // The tier is derived from the expiry, so this is enough.
                            gamer.setSubscriptionExpiresAt(clock.instant());
                        } else {
                            // Coins may already be spent, so this can go negative if we let it.
                            gamer.setCoin(Math.max(0, gamer.getCoin() - product.coins()));
                        }
                        gamers.save(gamer);
                    }));

            log.info("Refunded {} for {}", storeTransactionId, purchase.getUserId());
        });
    }

    /**
     * Moves an entitlement from one account to another.
     *
     * <p>Happens when somebody signs in to a second account on a device that already owns a
     * subscription. The store considers it one purchase, so two accounts must not both keep
     * it — the old one is expired as the new one is granted.
     */
    @Transactional
    public void transfer(String fromUserId, String toUserId) {
        expire(fromUserId);
        log.info("Entitlement transferred from {} to {}", fromUserId, toUserId);
    }
}
