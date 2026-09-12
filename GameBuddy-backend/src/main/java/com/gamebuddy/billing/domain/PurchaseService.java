package com.gamebuddy.billing.domain;

import com.gamebuddy.billing.infrastructure.entity.Purchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.billing.infrastructure.entity.PurchaseStatus;
import com.gamebuddy.billing.infrastructure.repository.PurchaseRepository;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private final CoinLedger coins;

    /**
     * What a verified purchase tells us, independent of who verified it.
     *
     * @param periodType RevenueCat's period_type; TRIAL is what separates a trial from a paid month
     * @param eventType RevenueCat's event type; RENEWAL is what makes month-2 retention countable
     */
    public record VerifiedPurchase(
            String userId,
            Product product,
            PurchasePlatform platform,
            String storeTransactionId,
            Instant purchasedAt,
            Instant expiresAt,
            String periodType,
            String eventType) {}

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
        purchase.setPeriodType(verified.periodType());
        purchase.setEventType(verified.eventType());

        Purchase saved;
        try {
            /*
             * Flushed here so a unique violation surfaces now, where it is a replay, rather
             * than escaping at commit time as a 500 that RevenueCat would retry.
             *
             * **The return value is the managed entity and `purchase` is not.** This row
             * carries its own assigned UUID and the class has no `@Version` and does not
             * implement `Persistable`, so Spring Data asks `isNew()`, sees a non-null id,
             * and takes it for an update: `save` therefore goes through `merge`, which
             * copies the state onto a *different* instance and leaves this one detached.
             * Writing the entitlement expiry onto `purchase` below wrote it to an object
             * nothing was going to persist, so every subscription in the ledger recorded a
             * null expiry while the gamer's own row was granted correctly. Silent, and
             * invisible until somebody asks the ledger when an entitlement should have
             * ended — a refund or chargeback question, which is exactly when the record
             * needs to be right.
             */
            saved = purchases.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent delivery of {} treated as a replay", verified.storeTransactionId());
            return false;
        }

        if (product.isSubscription()) {
            saved.setEntitlementExpiresAt(grantSubscription(gamer, product, verified.expiresAt()));
        } else {
            coins.earn(gamer, product.coins(), CoinReason.COIN_PACK);
        }
        gamers.save(gamer);

        log.info(
                "Granted {} to {} (transaction {})",
                product.storeId(),
                verified.userId(),
                verified.storeTransactionId());
        return true;
    }

    /**
     * Extends the paid period.
     *
     * @param storeExpiry the store's own expiry, authoritative when present
     */
    private Instant grantSubscription(Gamer gamer, Product product, Instant storeExpiry) {
        if (storeExpiry != null) {
            gamer.setSubscriptionTier(product.tier());
            gamer.setSubscriptionExpiresAt(storeExpiry);
            return storeExpiry;
        }
        return extendGold(gamer, product.period());
    }

    /**
     * Adds a period of Gold to whatever the account already holds, and returns the new
     * expiry. Does not save — the caller owns the transaction and the gamer.
     *
     * <p>Extended from the later of "now" and the existing expiry. Using the existing
     * expiry alone would back-date a renewal made after a lapse; using now alone would
     * throw away time an early renewer had already paid for.
     *
     * <p>Public and shared with the promotion codes because there must be exactly one
     * answer to "what does another month mean for somebody who already has three weeks".
     * The alternative was a second copy of these six lines in the promo service, which
     * would agree with this one on the day it was written and not for much longer.
     */
    public Instant extendGold(Gamer gamer, Duration period) {
        Instant now = clock.instant();

        SubscriptionTier current =
                SubscriptionTier.effective(gamer.getSubscriptionTier(), gamer.getSubscriptionExpiresAt(), now);
        Instant base = current == SubscriptionTier.BASIC || gamer.getSubscriptionExpiresAt() == null
                ? now
                : gamer.getSubscriptionExpiresAt();
        Instant expiresAt = base.plus(period);

        gamer.setSubscriptionTier(SubscriptionTier.GOLD);
        gamer.setSubscriptionExpiresAt(expiresAt);
        return expiresAt;
    }

    /**
     * Ends a subscription now, without touching the purchase ledger.
     *
     * <p>For expiry: the paid period simply ran out. The row stays {@code GRANTED} because
     * it was — the gamer had every day they paid for.
     *
     * <p>The tier column is cleared alongside the expiry. Nothing in the application needs
     * it — every reader goes through {@link SubscriptionTier#effective} — but a stored
     * {@code GOLD} on somebody who is not a member reads as true to anyone querying the
     * table directly, and support tools and ad-hoc counts are written against exactly that
     * column. This only covers the paths that pass through here; a subscription whose expiry
     * quietly passes with no webhook is never revisited, which is why the column also
     * carries a comment saying it is not the source of truth.
     */
    @Transactional
    public void expire(String userId) {
        gamers.findById(userId).ifPresent(gamer -> {
            if (gamer.getSubscriptionExpiresAt() != null
                    && gamer.getSubscriptionExpiresAt().isAfter(clock.instant())) {
                gamer.setSubscriptionExpiresAt(clock.instant());
                gamer.setSubscriptionTier(SubscriptionTier.BASIC);
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
        purchases
                .findByPlatformAndStoreTransactionId(platform, storeTransactionId)
                .ifPresent(purchase -> {
                    // Idempotent: a resent refund webhook must not reverse the same purchase
                    // twice. Expiring an already-expired sub is harmless, but a second
                    // `coins.spend` would dock a coin balance that may have been topped up
                    // since — punishing the gamer for RevenueCat redelivering. The row already
                    // records the reversal.
                    if (purchase.getStatus() == PurchaseStatus.REFUNDED) {
                        return;
                    }
                    purchase.setStatus(PurchaseStatus.REFUNDED);

                    gamers.findById(purchase.getUserId()).ifPresent(gamer -> Product.byStoreId(purchase.getProductId())
                            .ifPresent(product -> {
                                if (product.isSubscription()) {
                                    // The tier is derived from the expiry, so this is enough.
                                    gamer.setSubscriptionExpiresAt(clock.instant());
                                } else {
                                    // Coins may already be spent, so this can go negative if we let it.
                                    coins.spend(gamer, product.coins(), CoinReason.REFUND);
                                }
                                gamers.save(gamer);
                            }));

                    log.info("Refunded {} for {}", storeTransactionId, purchase.getUserId());
                });
    }

    /**
     * Moves an entitlement from one set of accounts to another.
     *
     * <p>Happens when somebody signs in to a second account on a device that already owns a
     * subscription. The store considers it one purchase, so two accounts must not both keep
     * it — the old ones are expired and the new ones receive what they held.
     *
     * <p><b>The receiving side is granted here, from our own records, because nothing else
     * will do it.</b> This used to expire the loser and stop, on the assumption that the
     * gainer's purchase event would follow. For the Play Store and the App Store it does
     * not: RevenueCat's {@code TRANSFER} is the only webhook the destination gets, and it
     * carries no product, no expiry and no transaction — just the two lists of ids. The
     * first real purchase after launch went exactly this way: granted to the id the device
     * was still signed in as, transferred three seconds later, and the account that paid
     * was left on BASIC until the next renewal, which for a yearly plan is a year away.
     *
     * <p>So the loser's entitlement is read before it is expired and written onto the
     * gainer, later expiry winning if the gainer already holds something. The ledger rows
     * still inside their paid period move with it, so a later refund of that transaction
     * revokes the account that now holds it rather than the one that used to.
     *
     * <p><b>The monthly-stipend clock moves too.</b> The Gold stipend (600 coins per 30 days,
     * see {@code CoinEarningService}) is timed per account by {@code stipendClaimedAt}. Left
     * behind, a subscription bounced onto a fresh account would let that account claim a
     * second stipend this cycle — one paid subscription minting the monthly grant again on
     * every account it touches. Carrying the later of the two clocks means moving the
     * membership moves its stipend cadence with it.
     */
    @Transactional
    public void transfer(List<String> fromUserIds, List<String> toUserIds) {
        Instant now = clock.instant();

        // What is being moved: the latest live expiry among the losers, its tier, and the
        // stipend clock that belongs with it. Read before `expire`, which overwrites the
        // subscription fields.
        SubscriptionTier movedTier = null;
        Instant movedExpiry = null;
        Instant movedStipendAt = null;
        List<Purchase> movedRows = new ArrayList<>();

        for (String from : fromUserIds) {
            Gamer loser = gamers.findById(from).orElse(null);
            if (loser == null) {
                // An id we never granted to — a RevenueCat anonymous id, most likely.
                // Nothing of ours to move.
                continue;
            }
            SubscriptionTier tier =
                    SubscriptionTier.effective(loser.getSubscriptionTier(), loser.getSubscriptionExpiresAt(), now);
            if (tier != SubscriptionTier.BASIC
                    && (movedExpiry == null || loser.getSubscriptionExpiresAt().isAfter(movedExpiry))) {
                movedTier = tier;
                movedExpiry = loser.getSubscriptionExpiresAt();
                movedStipendAt = loser.getStipendClaimedAt();
            }
            for (Purchase row : purchases.findByUserIdOrderByPurchasedAtDesc(from)) {
                if (row.getStatus() == PurchaseStatus.GRANTED
                        && row.getEntitlementExpiresAt() != null
                        && row.getEntitlementExpiresAt().isAfter(now)) {
                    movedRows.add(row);
                }
            }
            expire(from);
        }

        log.info("Entitlement transferred from {} to {}", fromUserIds, toUserIds);

        if (movedExpiry == null) {
            log.info("Nothing live to move; the receiving side keeps what it has");
            return;
        }

        String owner = null;
        for (String to : toUserIds) {
            Gamer gainer = gamers.findById(to).orElse(null);
            if (gainer == null) {
                log.warn("Transfer destination {} is not an account we know; nothing granted", to);
                continue;
            }
            SubscriptionTier held =
                    SubscriptionTier.effective(gainer.getSubscriptionTier(), gainer.getSubscriptionExpiresAt(), now);
            // Never shorten what they already have: somebody who bought on this account
            // and then triggered a transfer of an older plan keeps their own expiry.
            if (held == SubscriptionTier.BASIC || gainer.getSubscriptionExpiresAt().isBefore(movedExpiry)) {
                gainer.setSubscriptionTier(movedTier);
                gainer.setSubscriptionExpiresAt(movedExpiry);
                // Carry the stipend clock with the membership — the later of the two, so the
                // gainer can never claim the monthly grant sooner than the moved subscription
                // already allows. Without this a fresh account (null clock) could claim 600
                // immediately after a transfer the losing account had already claimed.
                if (movedStipendAt != null
                        && (gainer.getStipendClaimedAt() == null
                                || movedStipendAt.isAfter(gainer.getStipendClaimedAt()))) {
                    gainer.setStipendClaimedAt(movedStipendAt);
                }
                gamers.save(gainer);
                log.info("Granted {} until {} to {} by transfer", movedTier, movedExpiry, to);
            }
            if (owner == null) {
                owner = to;
            }
        }

        if (owner != null) {
            for (Purchase row : movedRows) {
                row.setUserId(owner);
            }
            purchases.saveAll(movedRows);
        }
    }
}
