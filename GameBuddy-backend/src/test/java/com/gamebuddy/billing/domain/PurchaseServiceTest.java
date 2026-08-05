package com.gamebuddy.billing.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.billing.infrastructure.entity.Purchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.billing.infrastructure.entity.PurchaseStatus;
import com.gamebuddy.billing.infrastructure.repository.PurchaseRepository;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PurchaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String USER = "gamer-1";

    private PurchaseRepository purchases;
    private GamerRepository gamers;
    private Gamer gamer;

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
    }

    /** A verifier that agrees with whatever is claimed. */
    private ReceiptVerifier acceptingVerifier(PurchasePlatform platform, Instant expiry) {
        ReceiptVerifier verifier = mock(ReceiptVerifier.class);
        when(verifier.platform()).thenReturn(platform);
        when(verifier.verify(any(), any()))
                .thenAnswer(i ->
                        new ReceiptVerifier.VerifiedPurchase("txn-1", i.getArgument(1, Product.class), NOW, expiry));
        return verifier;
    }

    private PurchaseService service(ReceiptVerifier... verifiers) {
        return new PurchaseService(purchases, gamers, Clock.fixed(NOW, ZoneOffset.UTC), List.of(verifiers));
    }

    // -- the security-critical paths ----------------------------------------

    @Test
    @DisplayName("with no verifier configured, every purchase is rejected rather than trusted")
    void failsClosedWithoutAVerifier() {
        PurchaseService service = service();

        assertThrows(
                BusinessException.class,
                () -> service.redeem(USER, PurchasePlatform.APPLE_APP_STORE, "gamebuddy.gold.monthly", "receipt"));
        verify(purchases, never()).saveAndFlush(any());
        assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
    }

    @Test
    @DisplayName("a platform with no verifier is rejected even when another platform has one")
    void rejectsUnconfiguredPlatform() {
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, null));

        assertThrows(
                BusinessException.class,
                () -> service.redeem(USER, PurchasePlatform.APPLE_APP_STORE, "gamebuddy.gold.monthly", "receipt"));
    }

    @Test
    @DisplayName("if the store reports a different product than the client claimed, nothing is granted")
    void refusesWhenStoreDisagreesWithTheClient() {
        ReceiptVerifier verifier = mock(ReceiptVerifier.class);
        when(verifier.platform()).thenReturn(PurchasePlatform.GOOGLE_PLAY);
        // Client asks for Gold; the store says it was actually a small coin pack.
        when(verifier.verify(any(), any()))
                .thenReturn(new ReceiptVerifier.VerifiedPurchase("txn-1", Product.COINS_SMALL, NOW, null));

        PurchaseService service = service(verifier);

        assertThrows(
                BusinessException.class,
                () -> service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "gamebuddy.gold.monthly", "receipt"));
        assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier());
        assertEquals(100, gamer.getCoin());
    }

    @Test
    void unknownProductIsRejectedBeforeAnythingElseHappens() {
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, null));

        assertThrows(
                BusinessException.class,
                () -> service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "not.a.real.product", "receipt"));
        verifyNoInteractions(purchases);
    }

    // -- idempotency ---------------------------------------------------------

    @Test
    @DisplayName("a receipt already redeemed is refused, so one payment cannot be spent twice")
    void replayIsRefused() {
        when(purchases.existsByPlatformAndStoreTransactionId(any(), eq("txn-1")))
                .thenReturn(true);
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, null));

        assertThrows(
                BusinessException.class,
                () -> service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "gamebuddy.coins.500", "receipt"));
        assertEquals(100, gamer.getCoin(), "no coins granted on a replay");
    }

    @Test
    @DisplayName("a concurrent redemption that loses the unique constraint reports a replay, not a 500")
    void concurrentRedemptionSurfacesAsAlreadyProcessed() {
        when(purchases.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, null));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "gamebuddy.coins.500", "receipt"));
        assertEquals(162, ex.getTransactionCode().getId());
    }

    // -- granting ------------------------------------------------------------

    @Test
    void subscriptionGrantsTheTierAndAnExpiry() {
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, null));

        Purchase purchase = service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "gamebuddy.gold.monthly", "receipt");

        assertEquals(SubscriptionTier.GOLD, gamer.getSubscriptionTier());
        assertEquals(NOW.plus(Duration.ofDays(30)), gamer.getSubscriptionExpiresAt());
        assertEquals(PurchaseStatus.GRANTED, purchase.getStatus());
    }

    @Test
    @DisplayName("the store's own expiry wins, because it knows about renewals we do not")
    void storeExpiryIsAuthoritative() {
        Instant storeExpiry = NOW.plus(Duration.ofDays(400));
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, storeExpiry));

        service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "gamebuddy.gold.monthly", "receipt");

        assertEquals(storeExpiry, gamer.getSubscriptionExpiresAt());
    }

    @Test
    @DisplayName("renewing early adds to the time left instead of truncating it")
    void earlyRenewalExtendsFromTheExistingExpiry() {
        gamer.setSubscriptionTier(SubscriptionTier.GOLD);
        gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(10)));
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, null));

        service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "gamebuddy.gold.monthly", "receipt");

        assertEquals(NOW.plus(Duration.ofDays(40)), gamer.getSubscriptionExpiresAt());
    }

    @Test
    @DisplayName("renewing after a lapse starts from now, not from the old expiry in the past")
    void lapsedRenewalDoesNotBackdate() {
        gamer.setSubscriptionTier(SubscriptionTier.GOLD);
        gamer.setSubscriptionExpiresAt(NOW.minus(Duration.ofDays(60)));
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, null));

        service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "gamebuddy.gold.monthly", "receipt");

        assertEquals(NOW.plus(Duration.ofDays(30)), gamer.getSubscriptionExpiresAt());
    }

    @Test
    void coinPackCreditsTheBalance() {
        PurchaseService service = service(acceptingVerifier(PurchasePlatform.GOOGLE_PLAY, null));

        service.redeem(USER, PurchasePlatform.GOOGLE_PLAY, "gamebuddy.coins.500", "receipt");

        assertEquals(600, gamer.getCoin());
        assertEquals(SubscriptionTier.BASIC, gamer.getSubscriptionTier(), "a consumable grants no tier");
    }

    // -- refunds -------------------------------------------------------------

    @Test
    @DisplayName("a refunded subscription expires immediately")
    void refundExpiresSubscription() {
        gamer.setSubscriptionTier(SubscriptionTier.GOLD);
        gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(20)));

        Purchase purchase = new Purchase();
        purchase.setUserId(USER);
        purchase.setProductId("gamebuddy.gold.monthly");
        purchase.setStatus(PurchaseStatus.GRANTED);
        when(purchases.findByPlatformAndStoreTransactionId(any(), any())).thenReturn(Optional.of(purchase));

        service().refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");

        assertEquals(PurchaseStatus.REFUNDED, purchase.getStatus());
        assertEquals(
                SubscriptionTier.BASIC,
                SubscriptionTier.effective(gamer.getSubscriptionTier(), gamer.getSubscriptionExpiresAt(), NOW));
    }

    @Test
    @DisplayName("refunding coins already spent does not push the balance negative")
    void refundDoesNotGoNegative() {
        gamer.setCoin(10);

        Purchase purchase = new Purchase();
        purchase.setUserId(USER);
        purchase.setProductId("gamebuddy.coins.500");
        purchase.setStatus(PurchaseStatus.GRANTED);
        when(purchases.findByPlatformAndStoreTransactionId(any(), any())).thenReturn(Optional.of(purchase));

        service().refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");

        assertEquals(0, gamer.getCoin());
    }

    @Test
    @DisplayName("the refunded row is kept, so the same receipt cannot be redeemed again")
    void refundKeepsTheRowClaimed() {
        Purchase purchase = new Purchase();
        purchase.setUserId(USER);
        purchase.setProductId("gamebuddy.coins.500");
        purchase.setStatus(PurchaseStatus.GRANTED);
        when(purchases.findByPlatformAndStoreTransactionId(any(), any())).thenReturn(Optional.of(purchase));

        service().refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");

        verify(purchases, never()).delete(any());
        assertEquals(PurchaseStatus.REFUNDED, purchase.getStatus());
    }
}
