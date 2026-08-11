package com.gamebuddy.billing.domain;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.billing.domain.PurchaseService.VerifiedPurchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.billing.interfaces.request.RevenueCatWebhook;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("RevenueCatService")
class RevenueCatServiceTest {

    private static final long PURCHASED_MS = Instant.parse("2026-08-01T12:00:00Z").toEpochMilli();
    private static final long EXPIRES_MS = Instant.parse("2026-08-31T12:00:00Z").toEpochMilli();

    private PurchaseService purchases;
    private RevenueCatService service;

    @BeforeEach
    void setUp() {
        purchases = mock(PurchaseService.class);
        service = new RevenueCatService(purchases);
    }

    private RevenueCatWebhook.Event event(String type, String productId, String store, String cancelReason) {
        return new RevenueCatWebhook.Event(
                "evt-1",
                type,
                "gamer-1",
                productId,
                PURCHASED_MS,
                EXPIRES_MS,
                store,
                "txn-1",
                "txn-1",
                List.of("gold"),
                cancelReason,
                "NORMAL",
                null,
                null);
    }

    @Nested
    @DisplayName("events that grant")
    class Granting {

        @Test
        @DisplayName("an initial purchase is granted with the store's own expiry")
        void initialPurchase() {
            service.handle(event("INITIAL_PURCHASE", "gamebuddy.gold.monthly", "PLAY_STORE", null));

            ArgumentCaptor<VerifiedPurchase> captured = ArgumentCaptor.forClass(VerifiedPurchase.class);
            verify(purchases).grant(captured.capture());

            VerifiedPurchase granted = captured.getValue();
            org.junit.jupiter.api.Assertions.assertEquals("gamer-1", granted.userId());
            org.junit.jupiter.api.Assertions.assertEquals(Product.GOLD_MONTHLY, granted.product());
            org.junit.jupiter.api.Assertions.assertEquals(PurchasePlatform.GOOGLE_PLAY, granted.platform());
            org.junit.jupiter.api.Assertions.assertEquals(Instant.ofEpochMilli(EXPIRES_MS), granted.expiresAt());
        }

        @Test
        @DisplayName("renewals, uncancellations and product changes all grant")
        void everythingThatMeansTheyHaveIt() {
            for (String type : new String[] {"RENEWAL", "UNCANCELLATION", "PRODUCT_CHANGE", "NON_RENEWING_PURCHASE"}) {
                reset(purchases);
                service.handle(event(type, "gamebuddy.gold.monthly", "APP_STORE", null));
                verify(purchases, description(type + " should grant")).grant(any());
            }
        }

        @Test
        @DisplayName("an unknown product grants nothing rather than guessing")
        void unknownProduct() {
            service.handle(event("INITIAL_PURCHASE", "gamebuddy.gold.lifetime", "PLAY_STORE", null));
            verify(purchases, never()).grant(any());
        }

        @Test
        @DisplayName("a store we do not sell on grants nothing")
        void unsupportedStore() {
            // The purchase.platform check constraint allows two values; inventing a third
            // would fail at insert with a constraint violation instead of a clear log.
            service.handle(event("INITIAL_PURCHASE", "gamebuddy.gold.monthly", "STRIPE", null));
            verify(purchases, never()).grant(any());
        }

        @Test
        @DisplayName("an anonymous buyer grants nothing — there is no account to grant to")
        void anonymousUser() {
            RevenueCatWebhook.Event anonymous = new RevenueCatWebhook.Event(
                    "evt-1",
                    "INITIAL_PURCHASE",
                    "$RCAnonymousID:abc123",
                    "gamebuddy.gold.monthly",
                    PURCHASED_MS,
                    EXPIRES_MS,
                    "PLAY_STORE",
                    "txn-1",
                    "txn-1",
                    List.of(),
                    null,
                    "NORMAL",
                    null,
                    null);

            service.handle(anonymous);
            verify(purchases, never()).grant(any());
        }
    }

    @Nested
    @DisplayName("events that take it away")
    class Revoking {

        @Test
        @DisplayName("expiration ends the subscription")
        void expiration() {
            service.handle(event("EXPIRATION", "gamebuddy.gold.monthly", "PLAY_STORE", null));
            verify(purchases).expire("gamer-1");
        }

        @Test
        @DisplayName("cancelling auto-renew does NOT revoke — they keep what they paid for")
        void unsubscribeKeepsAccess() {
            // The mistake worth failing loudly on. A cancellation means auto-renew is off,
            // not that access ended; EXPIRATION says that, later. Revoking here would take
            // days off somebody who has already paid for them.
            service.handle(event("CANCELLATION", "gamebuddy.gold.monthly", "PLAY_STORE", "UNSUBSCRIBE"));

            verify(purchases, never()).expire(any());
            verify(purchases, never()).refund(any(), any());
        }

        @Test
        @DisplayName("a billing problem does not revoke either — the store is still retrying")
        void billingIssueKeepsAccess() {
            service.handle(event("BILLING_ISSUE", "gamebuddy.gold.monthly", "PLAY_STORE", null));
            verify(purchases, never()).expire(any());
        }

        @Test
        @DisplayName("a support refund does revoke, and marks the transaction")
        void refundRevokes() {
            service.handle(event("CANCELLATION", "gamebuddy.gold.monthly", "PLAY_STORE", "CUSTOMER_SUPPORT"));
            verify(purchases).refund(PurchasePlatform.GOOGLE_PLAY, "txn-1");
        }
    }

    @Nested
    @DisplayName("robustness")
    class Robustness {

        @Test
        @DisplayName("an unknown event type is ignored, not rejected")
        void unknownTypeIsIgnored() {
            // RevenueCat adds event types. Throwing would make it retry forever and
            // eventually disable the webhook, taking the events we do handle with it.
            service.handle(event("SOMETHING_NEW_IN_2027", "gamebuddy.gold.monthly", "PLAY_STORE", null));
            verifyNoInteractions(purchases);
        }

        @Test
        @DisplayName("a malformed event with no type is ignored")
        void noTypeIsIgnored() {
            service.handle(event(null, "gamebuddy.gold.monthly", "PLAY_STORE", null));
            verifyNoInteractions(purchases);
        }

        @Test
        @DisplayName("a null event is ignored")
        void nullEventIsIgnored() {
            service.handle(null);
            verifyNoInteractions(purchases);
        }
    }

    @Nested
    @DisplayName("transfers")
    class Transfers {

        @Test
        @DisplayName("the losing account is expired, so one purchase never entitles two")
        void transferExpiresTheOldAccount() {
            RevenueCatWebhook.Event transfer = new RevenueCatWebhook.Event(
                    "evt-1",
                    "TRANSFER",
                    "gamer-new",
                    "gamebuddy.gold.monthly",
                    PURCHASED_MS,
                    EXPIRES_MS,
                    "PLAY_STORE",
                    "txn-1",
                    "txn-1",
                    List.of("gold"),
                    null,
                    "NORMAL",
                    List.of("gamer-old"),
                    List.of("gamer-new"));

            service.handle(transfer);
            verify(purchases).transfer("gamer-old", "gamer-new");
        }
    }
}
