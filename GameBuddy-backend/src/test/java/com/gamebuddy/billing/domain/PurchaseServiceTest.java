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
import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    private GamerCosmeticRepository ownership;
    private Gamer gamer;
    private PurchaseService service;

    @BeforeEach
    void setUp() {
        purchases = mock(PurchaseRepository.class);
        gamers = mock(GamerRepository.class);
        ownership = mock(GamerCosmeticRepository.class);

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
        service = new PurchaseService(purchases, gamers, clock, coins, ownership);
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
            verify(ownership).grantMembershipCosmeticsForUser(USER, NOW);
        }

        @Test
        @DisplayName("a consumable adds coins and grants no tier")
        void grantsCoins() {
            assertTrue(service.grant(purchase(Product.COINS_SMALL, null)));

            assertEquals(100 + Product.COINS_SMALL.coins(), gamer.getCoin());
            assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
            assertNull(gamer.getSubscriptionExpiresAt());
            verify(ownership, never()).grantMembershipCosmeticsForUser(anyString(), any());
        }

        @Test
        @DisplayName("the ledger keeps the entitlement expiry, on the entity JPA actually persists")
        void recordsEntitlementExpiryOnTheManagedEntity() {
            Instant storeExpiry = NOW.plus(Duration.ofDays(30));

            /*
             * The stub has to hand back a *different* instance, because that is what the
             * real repository does here and it is the whole point of the test.
             *
             * A Purchase carries an assigned UUID and is neither versioned nor
             * Persistable, so Spring Data reads the non-null id as "already exists" and
             * routes save through merge — which copies the state onto a managed instance
             * and leaves the caller's object detached. The default stub in setUp returns
             * the argument, which quietly makes the detached object and the managed one
             * the same thing and lets a write to the wrong one look correct.
             */
            Purchase managed = new Purchase();
            when(purchases.saveAndFlush(any())).thenAnswer(i -> {
                Purchase detached = i.getArgument(0);
                managed.setId(detached.getId());
                managed.setUserId(detached.getUserId());
                managed.setProductId(detached.getProductId());
                return managed;
            });

            assertTrue(service.grant(purchase(Product.GOLD_MONTHLY, storeExpiry)));

            assertEquals(storeExpiry, managed.getEntitlementExpiresAt());
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
            verify(ownership, never()).grantMembershipCosmeticsForUser(anyString(), any());
        }

        @Test
        @DisplayName("two deliveries racing: the loser grants nothing")
        void concurrentDeliveryIsIgnored() {
            when(purchases.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

            assertFalse(service.grant(purchase(Product.GOLD_MONTHLY, NOW.plus(Duration.ofDays(30)))));
            assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
            verify(ownership, never()).grantMembershipCosmeticsForUser(anyString(), any());
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
            verify(ownership).revokeLapsedMembershipCosmeticsForUser(USER, NOW);
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
            verify(ownership).revokeLapsedMembershipCosmeticsForUser(USER, NOW);
        }

        @Test
        @DisplayName("an expired webhook removes Gold items and unequips them immediately")
        void expirationCleansCosmetics() {
            Cosmetic frame = new Cosmetic();
            frame.setId(UUID.randomUUID());
            Cosmetic banner = new Cosmetic();
            banner.setId(UUID.randomUUID());
            Cosmetic theme = new Cosmetic();
            theme.setId(UUID.randomUUID());
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.minusSeconds(1));
            gamer.setEquippedFrame(frame);
            gamer.setEquippedBanner(banner);
            gamer.setEquippedTheme(theme);
            when(ownership.findOwnedIds(USER)).thenReturn(Set.of(theme.getId()));

            service.expireFromWebhook(USER, NOW.minusSeconds(1));

            verify(ownership).revokeLapsedMembershipCosmeticsForUser(USER, NOW);
            assertNull(gamer.getEquippedFrame());
            assertNull(gamer.getEquippedBanner());
            assertEquals(theme, gamer.getEquippedTheme());
        }

        @Test
        @DisplayName("a delayed expiration from an older period cannot revoke a renewal")
        void staleExpirationKeepsRenewedMembership() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            service.expireFromWebhook(USER, NOW.minusSeconds(1));

            assertEquals(SubscriptionTier.GOLD, gamer.getSubscriptionTier());
            assertEquals(NOW.plus(Duration.ofDays(30)), gamer.getSubscriptionExpiresAt());
            verify(ownership, never()).revokeLapsedMembershipCosmeticsForUser(anyString(), any());
        }

        @Test
        @DisplayName("an expiration without a period does not revoke a current member")
        void missingExpirationDateKeepsMembership() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            service.expireFromWebhook(USER, null);

            assertEquals(SubscriptionTier.GOLD, gamer.getSubscriptionTier());
            verify(ownership, never()).revokeLapsedMembershipCosmeticsForUser(anyString(), any());
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
            Cosmetic frame = new Cosmetic();
            frame.setId(UUID.randomUUID());
            gamer.setEquippedFrame(frame);
            when(ownership.findOwnedIds(USER)).thenReturn(Set.of());

            service.refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");

            assertEquals(PurchaseStatus.REFUNDED, row.getStatus());
            assertEquals(NOW, gamer.getSubscriptionExpiresAt());
            assertNull(gamer.getEquippedFrame());
            verify(ownership).revokeLapsedMembershipCosmeticsForUser(USER, NOW);
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

        @Test
        @DisplayName("a resent refund webhook reverses only once")
        void refundIsIdempotent() {
            Purchase row = new Purchase();
            row.setUserId(USER);
            row.setProductId(Product.COINS_LARGE.storeId());
            row.setStatus(PurchaseStatus.GRANTED);
            when(purchases.findByPlatformAndStoreTransactionId(PurchasePlatform.GOOGLE_PLAY, "txn-1"))
                    .thenReturn(Optional.of(row));

            gamer.setCoin(10_000);
            service.refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");
            int afterFirst = gamer.getCoin();

            // RevenueCat redelivers the same refund. The row is already REFUNDED, so the
            // coins must not be docked a second time.
            service.refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");

            assertEquals(afterFirst, gamer.getCoin(), "a second refund must not dock coins again");
            assertEquals(PurchaseStatus.REFUNDED, row.getStatus());
        }
    }

    /**
     * The one path where the gaining side gets no purchase event of its own — see
     * {@link PurchaseService#transfer}. What the loser held has to arrive on the gainer
     * from here, or it arrives nowhere.
     */
    @Nested
    @DisplayName("transferring between accounts")
    class Transferring {

        private static final String EVENT = "transfer-1";
        private static final String OTHER = "gamer-2";
        private Gamer other;

        @BeforeEach
        void otherAccount() {
            other = new Gamer();
            other.setUserId(OTHER);
            other.setSubscriptionTier(SubscriptionTier.BASIC);
            when(gamers.findById(OTHER)).thenReturn(Optional.of(other));
            when(purchases.findByUserIdOrderByPurchasedAtDesc(any())).thenReturn(java.util.List.of());
            Purchase legacy = liveGoldPurchase(USER, null);
            when(purchases.findByUserIdOrderByPurchasedAtDesc(USER)).thenReturn(java.util.List.of(legacy));
            when(purchases.claimTransferEvent(EVENT, NOW)).thenReturn(1);
        }

        private Purchase liveGoldPurchase(String userId, Instant expiry) {
            Purchase row = new Purchase();
            row.setUserId(userId);
            row.setProductId(Product.GOLD_MONTHLY.storeId());
            row.setStatus(PurchaseStatus.GRANTED);
            row.setEntitlementExpiresAt(expiry);
            return row;
        }

        @Test
        @DisplayName("the gainer receives exactly what the loser held, and the loser is expired")
        void movesTheEntitlement() {
            Instant paidUntil = NOW.plus(Duration.ofDays(300));
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(paidUntil);
            Cosmetic frame = new Cosmetic();
            frame.setId(UUID.randomUUID());
            gamer.setEquippedFrame(frame);
            when(ownership.findOwnedIds(USER)).thenReturn(Set.of());

            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));

            assertEquals(SubscriptionTier.GOLD, other.getSubscriptionTier());
            assertEquals(paidUntil, other.getSubscriptionExpiresAt());
            assertEquals(NOW, gamer.getSubscriptionExpiresAt());
            assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
            assertNull(gamer.getEquippedFrame());
            verify(ownership).revokeLapsedMembershipCosmeticsForUser(USER, NOW);
            verify(ownership).grantMembershipCosmeticsForUser(OTHER, NOW);
        }

        @Test
        @DisplayName("the ledger rows still inside their paid period follow the entitlement")
        void movesTheLedgerRows() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(300)));

            Purchase live = new Purchase();
            live.setUserId(USER);
            live.setProductId(Product.GOLD_MONTHLY.storeId());
            live.setStatus(PurchaseStatus.GRANTED);
            live.setEntitlementExpiresAt(NOW.plus(Duration.ofDays(300)));
            Purchase lapsed = new Purchase();
            lapsed.setUserId(USER);
            lapsed.setProductId(Product.GOLD_MONTHLY.storeId());
            lapsed.setStatus(PurchaseStatus.GRANTED);
            lapsed.setEntitlementExpiresAt(NOW.minus(Duration.ofDays(1)));
            when(purchases.findByUserIdOrderByPurchasedAtDesc(USER)).thenReturn(java.util.List.of(live, lapsed));

            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));

            // So a refund of that transaction later revokes the account that holds it.
            assertEquals(OTHER, live.getUserId());
            assertEquals(USER, lapsed.getUserId());
        }

        @Test
        @DisplayName("a gainer already holding a later expiry keeps it")
        void neverShortensWhatTheGainerHas() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(7)));
            Instant longer = NOW.plus(Duration.ofDays(300));
            other.setSubscriptionTier(SubscriptionTier.GOLD);
            other.setSubscriptionExpiresAt(longer);

            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));

            assertEquals(longer, other.getSubscriptionExpiresAt());
            assertEquals(NOW, gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("a loser with nothing live gives the gainer nothing")
        void nothingLiveMovesNothing() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.minus(Duration.ofDays(1)));

            assertThrows(
                    TransferPendingException.class,
                    () -> service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER)));

            assertEquals(SubscriptionTier.BASIC, other.getSubscriptionTier());
            assertNull(other.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("a source with no local entitlement is deferred, not guessed")
        void unknownSourceIsSkipped() {
            when(gamers.findById("$RCAnonymousID:abc")).thenReturn(Optional.empty());

            assertThrows(
                    TransferPendingException.class,
                    () -> service.transfer(EVENT, java.util.List.of("$RCAnonymousID:abc"), java.util.List.of(OTHER)));

            assertEquals(SubscriptionTier.BASIC, other.getSubscriptionTier());
        }

        @Test
        @DisplayName("a promo membership without a store purchase is not transferred")
        void promoGoldIsNotTransferredAsStoreGold() {
            when(purchases.findByUserIdOrderByPurchasedAtDesc(USER)).thenReturn(java.util.List.of());
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            assertThrows(
                    TransferPendingException.class,
                    () -> service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER)));

            assertEquals(SubscriptionTier.GOLD, gamer.getSubscriptionTier());
            assertEquals(SubscriptionTier.BASIC, other.getSubscriptionTier());
            verify(ownership, never()).revokeLapsedMembershipCosmeticsForUser(anyString(), any());
        }

        @Test
        @DisplayName("a deleted old account row is not needed when its purchase record is live")
        void recoversFromPurchaseWhenOldAccountIsGone() {
            Instant expiry = NOW.plus(Duration.ofDays(30));
            when(gamers.findById(USER)).thenReturn(Optional.empty());
            Purchase row = liveGoldPurchase(USER, expiry);
            when(purchases.findByUserIdOrderByPurchasedAtDesc(USER)).thenReturn(java.util.List.of(row));

            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));

            assertEquals(SubscriptionTier.GOLD, other.getSubscriptionTier());
            assertEquals(expiry, other.getSubscriptionExpiresAt());
            assertEquals(OTHER, row.getUserId());
            verify(ownership).grantMembershipCosmeticsForUser(OTHER, NOW);
            verify(ownership, never()).revokeLapsedMembershipCosmeticsForUser(eq(USER), any());
        }

        @Test
        @DisplayName("a soft-deleted old account loses Gold while the new one gains it")
        void revokesSoftDeletedSource() {
            gamer.setDeletedAt(NOW.minusSeconds(10));
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));

            assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
            assertEquals(SubscriptionTier.GOLD, other.getSubscriptionTier());
            verify(ownership).revokeLapsedMembershipCosmeticsForUser(USER, NOW);
            verify(ownership).grantMembershipCosmeticsForUser(OTHER, NOW);
        }

        @Test
        @DisplayName("a repeated webhook cannot move a new purchase on the former account")
        void duplicateEventDoesNothing() {
            when(purchases.claimTransferEvent(EVENT, NOW)).thenReturn(1, 0);
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(90)));
            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));

            assertEquals(NOW.plus(Duration.ofDays(30)), other.getSubscriptionExpiresAt());
            assertEquals(NOW.plus(Duration.ofDays(90)), gamer.getSubscriptionExpiresAt());
            verify(ownership).revokeLapsedMembershipCosmeticsForUser(USER, NOW);
            verify(ownership).grantMembershipCosmeticsForUser(OTHER, NOW);
        }

        @Test
        @DisplayName("two real destinations are ambiguous, so neither gets Gold")
        void multipleDestinationsAreRejected() {
            Gamer alias = new Gamer();
            alias.setUserId("another-real-account");
            when(gamers.findById(alias.getUserId())).thenReturn(Optional.of(alias));
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            assertThrows(
                    TransferPendingException.class,
                    () -> service.transfer(
                            EVENT, java.util.List.of(USER), java.util.List.of(OTHER, alias.getUserId())));

            assertEquals(SubscriptionTier.GOLD, gamer.getSubscriptionTier());
            assertEquals(SubscriptionTier.BASIC, other.getSubscriptionTier());
            assertEquals(SubscriptionTier.BASIC, alias.getSubscriptionTier());
            verify(ownership, never()).revokeLapsedMembershipCosmeticsForUser(anyString(), any());
        }

        @Test
        @DisplayName("a missing destination never strips Gold from the source")
        void missingDestinationIsDeferred() {
            when(gamers.findById(OTHER)).thenReturn(Optional.empty());
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            assertThrows(
                    TransferPendingException.class,
                    () -> service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER)));

            assertEquals(SubscriptionTier.GOLD, gamer.getSubscriptionTier());
            verify(ownership, never()).revokeLapsedMembershipCosmeticsForUser(anyString(), any());
        }

        @Test
        @DisplayName("the stipend clock moves with the membership, so a fresh account can't re-claim")
        void carriesStipendClock() {
            Instant claimed = NOW.minus(Duration.ofDays(2));
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(300)));
            gamer.setStipendClaimedAt(claimed);
            // `other` is a fresh account: a null stipend clock, which would otherwise let it
            // claim the monthly 600 the losing account already took from this subscription.

            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));

            assertEquals(claimed, other.getStipendClaimedAt(), "the gainer inherits the loser's stipend clock");
        }

        @Test
        @DisplayName("a gainer that claimed more recently keeps its own stipend clock")
        void keepsTheLaterStipendClock() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(300)));
            gamer.setStipendClaimedAt(NOW.minus(Duration.ofDays(20)));
            Instant recent = NOW.minus(Duration.ofDays(1));
            other.setStipendClaimedAt(recent);

            service.transfer(EVENT, java.util.List.of(USER), java.util.List.of(OTHER));

            assertEquals(recent, other.getStipendClaimedAt(), "never rewind the gainer's clock");
        }
    }
}
