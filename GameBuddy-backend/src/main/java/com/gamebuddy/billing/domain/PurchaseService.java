package com.gamebuddy.billing.domain;

import com.gamebuddy.billing.infrastructure.entity.Purchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.billing.infrastructure.entity.PurchaseStatus;
import com.gamebuddy.billing.infrastructure.repository.PurchaseRepository;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redeems a store purchase into an entitlement.
 *
 * <p>The order of operations is the whole design, and it is deliberate:
 *
 * <ol>
 *   <li><b>Verify with the store first.</b> Nothing is granted, and nothing is written, on
 *       the strength of what the client claims.
 *   <li><b>Record the transaction, then grant.</b> The unique constraint on the store's
 *       transaction id is what makes redemption idempotent, so it has to be taken before
 *       the entitlement is handed over. Granting first and recording afterwards leaves a
 *       window in which a retry grants twice.
 *   <li><b>Extend from whichever is later — the current expiry or now.</b> Renewing early
 *       must add to the remaining time rather than truncate it, and renewing after a lapse
 *       must not back-date the new period into the gap.
 * </ol>
 */
@Slf4j
@Service
public class PurchaseService {

    private final PurchaseRepository purchases;
    private final GamerRepository gamers;
    private final Clock clock;
    private final Map<PurchasePlatform, ReceiptVerifier> verifiers = new EnumMap<>(PurchasePlatform.class);

    public PurchaseService(
            PurchaseRepository purchases, GamerRepository gamers, Clock clock, List<ReceiptVerifier> verifiers) {
        this.purchases = purchases;
        this.gamers = gamers;
        this.clock = clock;
        verifiers.forEach(verifier -> this.verifiers.put(verifier.platform(), verifier));

        if (this.verifiers.isEmpty()) {
            // Not an exception: a service with no billing configured should still start and
            // serve everything else. It just cannot sell anything.
            log.warn("No receipt verifiers are registered; every purchase will be rejected.");
        }
    }

    /**
     * @param userId who is redeeming
     * @param platform which store the receipt came from
     * @param storeProductId the product id as the client reports it, checked against the receipt
     * @param receipt the opaque store payload
     */
    @Transactional
    public Purchase redeem(String userId, PurchasePlatform platform, String storeProductId, String receipt) {
        Product product = Product.byStoreId(storeProductId)
                .orElseThrow(() -> new BusinessException(TransactionCode.PRODUCT_NOT_FOUND));

        ReceiptVerifier verifier = verifiers.get(platform);
        if (verifier == null) {
            // Fails closed. An unconfigured platform must reject rather than trust.
            log.warn("Purchase rejected: no verifier configured for {}", platform);
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }

        ReceiptVerifier.VerifiedPurchase verified = verifier.verify(receipt, product);
        if (verified.product() != product) {
            // The store disagrees with the client about what was bought. Believe the store,
            // and refuse — otherwise a coin pack could be redeemed as a subscription.
            log.warn(
                    "Purchase rejected: client claimed {} but the store reported {}",
                    product.storeId(),
                    verified.product().storeId());
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }

        // Cheap pre-check for the common replay; the unique constraint below is what makes
        // it correct under concurrency.
        if (purchases.existsByPlatformAndStoreTransactionId(platform, verified.storeTransactionId())) {
            throw new BusinessException(TransactionCode.PURCHASE_ALREADY_PROCESSED);
        }

        Gamer gamer = gamers.findById(userId).orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        Purchase purchase = new Purchase();
        purchase.setId(UUID.randomUUID());
        purchase.setUserId(userId);
        purchase.setProductId(product.storeId());
        purchase.setPlatform(platform);
        purchase.setStoreTransactionId(verified.storeTransactionId());
        purchase.setStatus(PurchaseStatus.GRANTED);
        purchase.setPurchasedAt(verified.purchasedAt());
        purchase.setReceipt(truncate(receipt));

        try {
            // flush so the unique violation surfaces here, where it can be turned into a
            // clean "already processed" rather than escaping as a 500 at commit time.
            purchases.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent redemptions of the same receipt: the loser reports the replay.
            //
            // Worth a WARN even though it is handled. One is a double-tap on a slow
            // connection; a stream of them against one account is somebody replaying a
            // receipt to see whether it grants twice, and the count is the only signal.
            log.warn("Concurrent redemption of {} for {} rejected as a replay", verified.storeTransactionId(), userId);
            throw new BusinessException(TransactionCode.PURCHASE_ALREADY_PROCESSED, e);
        }

        if (product.isSubscription()) {
            Instant expiresAt = grantSubscription(gamer, product, verified.expiresAt());
            purchase.setEntitlementExpiresAt(expiresAt);
        } else {
            gamer.setCoin(gamer.getCoin() + product.coins());
        }
        gamers.save(gamer);

        log.info("Granted {} to {} (transaction {})", product.storeId(), userId, verified.storeTransactionId());
        return purchase;
    }

    /**
     * Extends the paid period.
     *
     * @param storeExpiry the store's own expiry, which is authoritative when present —
     *     Apple and Google know about renewals, grace periods and refunds that we do not
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
     * Revokes an entitlement after a refund or chargeback.
     *
     * <p>The row is kept and marked rather than deleted, so the transaction id stays
     * claimed and the same receipt cannot simply be redeemed again.
     */
    @Transactional
    public void refund(PurchasePlatform platform, String storeTransactionId) {
        purchases
                .findByPlatformAndStoreTransactionId(platform, storeTransactionId)
                .ifPresent(purchase -> {
                    purchase.setStatus(PurchaseStatus.REFUNDED);

                    gamers.findById(purchase.getUserId()).ifPresent(gamer -> {
                        Product.byStoreId(purchase.getProductId()).ifPresent(product -> {
                            if (product.isSubscription()) {
                                // Expire immediately. The tier is derived from the expiry, so this
                                // is enough — nothing else has to be written back.
                                gamer.setSubscriptionExpiresAt(clock.instant());
                            } else {
                                // Coins may already be spent, so this can go negative if we let it.
                                gamer.setCoin(Math.max(0, gamer.getCoin() - product.coins()));
                            }
                            gamers.save(gamer);
                        });
                    });

                    log.info("Refunded {} for {}", storeTransactionId, purchase.getUserId());
                });
    }

    private static String truncate(String receipt) {
        int max = 4000;
        return receipt.length() <= max ? receipt : receipt.substring(0, max);
    }
}
