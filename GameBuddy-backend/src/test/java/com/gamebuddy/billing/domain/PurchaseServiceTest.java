package com.gamebuddy.billing.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.billing.domain.PurchaseService.VerifiedPurchase;
import com.gamebuddy.billing.infrastructure.entity.Purchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.billing.infrastructure.entity.PurchaseStatus;
import com.gamebuddy.billing.infrastructure.repository.PurchaseRepository;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinLedgerRepository;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Granting a purchase that RevenueCat has already verified.
 *
 * <p>Nothing here tests receipt verification, because nothing here does any: that moved to
 * RevenueCat. What is left is the part that stayed ours and can still get somebody's money
 * wrong — idempotency, and how a paid period is extended.
 */
@DisplayName("PurchaseService")
class PurchaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String USER = "gamer-1";

    private PurchaseRepository purchases;
    private GamerRepository gamers;
    private Gamer gamer;
    private PurchaseService service;

    @BeforeEach
    void setUp() {
        purchases = mock(PurchaseRepository.class);
        gamers = mock(GamerRepository.class);

        gamer = new Gamer();
        gamer.setUserId(USER);
        gamer.setCoin(100);
        gamer.setSubscriptionTier(SubscriptionTier.BASIC);

        when(gamers.findById(USER)).thenReturn(Optional.of(gamer));
        when(purchases.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        // A real ledger over a mocked repository rather than a mock ledger: it is what
        // moves the balance now, and a stubbed one would make every coin assertion below
        // pass without anything happening.
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        CoinLedger coins = new CoinLedger(mock(CoinLedgerRepository.class), clock);
        service = new PurchaseService(purchases, gamers, clock, coins);
    }

    private VerifiedPurchase purchase(Product product, Instant expiresAt) {
        return new VerifiedPurchase(
                USER, product, PurchasePlatform.GOOGLE_PLAY, "txn-1", NOW, expiresAt, "NORMAL", "INITIAL_PURCHASE");
    }

    @Nested
    @DisplayName("granting")
    class Granting {

        @Test
        @DisplayName("a subscription sets the tier and the store's expiry")
        void grantsSubscription() {
            Instant storeExpiry = NOW.plus(Duration.ofDays(30));

            assertTrue(service.grant(purchase(Product.GOLD_MONTHLY, storeExpiry)));

            assertEquals(SubscriptionTier.GOLD, gamer.getSubscriptionTier());
            assertEquals(storeExpiry, gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("a consumable adds coins and grants no tier")
        void grantsCoins() {
            assertTrue(service.grant(purchase(Product.COINS_SMALL, null)));

            assertEquals(100 + Product.COINS_SMALL.coins(), gamer.getCoin());
            assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
            assertNull(gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("the transaction is recorded before the entitlement is handed over")
        void recordsBeforeGranting() {
            // The unique constraint is the idempotency mechanism, so it has to be taken
            // first. Granting first and recording after leaves a window where a retry
            // grants twice.
            service.grant(purchase(Product.GOLD_MONTHLY, NOW.plus(Duration.ofDays(30))));

            var order = inOrder(purchases, gamers);
            order.verify(purchases).saveAndFlush(any(Purchase.class));
            order.verify(gamers).save(gamer);
        }
    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        @DisplayName("a replayed webhook grants nothing and is not an error")
        void replayIsIgnored() {
            when(purchases.existsByPlatformAndStoreTransactionId(PurchasePlatform.GOOGLE_PLAY, "txn-1"))
                    .thenReturn(true);

            // False, not an exception. RevenueCat retries until it gets a 2xx, so a
            // duplicate is the normal case — throwing would make it retry forever.
            assertFalse(service.grant(purchase(Product.GOLD_MONTHLY, NOW.plus(Duration.ofDays(30)))));

            assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
            verify(purchases, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("two deliveries racing: the loser grants nothing")
        void concurrentDeliveryIsIgnored() {
            when(purchases.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

            assertFalse(service.grant(purchase(Product.GOLD_MONTHLY, NOW.plus(Duration.ofDays(30)))));
            assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
        }

        @Test
        @DisplayName("an event for an account that no longer exists is dropped, not retried")
        void unknownAccountIsDropped() {
            when(gamers.findById(USER)).thenReturn(Optional.empty());

            assertFalse(service.grant(purchase(Product.GOLD_MONTHLY, NOW.plus(Duration.ofDays(30)))));
            verify(purchases, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("extending a paid period without a store expiry")
    class Extending {

        @Test
        @DisplayName("renewing early adds to the time already paid for")
        void earlyRenewalExtends() {
            Instant existing = NOW.plus(Duration.ofDays(10));
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(existing);

            service.grant(purchase(Product.GOLD_MONTHLY, null));

            // Not truncated to now + 30. Somebody who renews with time left keeps it.
            assertEquals(existing.plus(Duration.ofDays(30)), gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("renewing after a lapse starts from now, not from the old expiry")
        void lapsedRenewalDoesNotBackdate() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.minus(Duration.ofDays(60)));

            service.grant(purchase(Product.GOLD_MONTHLY, null));

            // Back-dating would sell somebody thirty days and hand them none of them.
            assertEquals(NOW.plus(Duration.ofDays(30)), gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("the store's expiry wins whenever it gives one")
        void storeExpiryWins() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(10)));
            Instant storeExpiry = NOW.plus(Duration.ofDays(3));

            service.grant(purchase(Product.GOLD_MONTHLY, storeExpiry));

            // Even when it is sooner than ours. The store knows about refunds, grace
            // periods and billing retries; we are guessing.
            assertEquals(storeExpiry, gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("a week is a week")
        void weeklyGrantsSevenDays() {
            service.grant(purchase(Product.GOLD_WEEKLY, null));
            assertEquals(NOW.plus(Duration.ofDays(7)), gamer.getSubscriptionExpiresAt());
        }
    }

    @Nested
    @DisplayName("taking it away")
    class Revoking {

        @Test
        @DisplayName("expiry ends the subscription now")
        void expireEndsIt() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(10)));

            service.expire(USER);

            assertEquals(NOW, gamer.getSubscriptionExpiresAt());
            assertEquals(
                    SubscriptionTier.BASIC,
                    SubscriptionTier.effective(
                            gamer.getSubscriptionTier(), gamer.getSubscriptionExpiresAt(), NOW.plusSeconds(1)));
        }

        @Test
        @DisplayName("expiring an already-lapsed account does not move the date")
        void expireIsIdempotent() {
            Instant lapsed = NOW.minus(Duration.ofDays(5));
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(lapsed);

            service.expire(USER);

            // Pushing it forward to now would silently hand back five days.
            assertEquals(lapsed, gamer.getSubscriptionExpiresAt());
            verify(gamers, never()).save(any());
        }

        @Test
        @DisplayName("a refund expires the subscription and marks the row")
        void refundRevokesSubscription() {
            Purchase row = new Purchase();
            row.setUserId(USER);
            row.setProductId(Product.GOLD_MONTHLY.storeId());
            row.setStatus(PurchaseStatus.GRANTED);
            when(purchases.findByPlatformAndStoreTransactionId(PurchasePlatform.GOOGLE_PLAY, "txn-1"))
                    .thenReturn(Optional.of(row));

            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(20)));

            service.refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");

            assertEquals(PurchaseStatus.REFUNDED, row.getStatus());
            assertEquals(NOW, gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("refunded coins cannot push a balance negative")
        void refundedCoinsFloorAtZero() {
            Purchase row = new Purchase();
            row.setUserId(USER);
            row.setProductId(Product.COINS_LARGE.storeId());
            row.setStatus(PurchaseStatus.GRANTED);
            when(purchases.findByPlatformAndStoreTransactionId(PurchasePlatform.GOOGLE_PLAY, "txn-1"))
                    .thenReturn(Optional.of(row));

            // Already spent most of them.
            gamer.setCoin(50);

            service.refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");

            assertEquals(0, gamer.getCoin());
        }
    }
}
