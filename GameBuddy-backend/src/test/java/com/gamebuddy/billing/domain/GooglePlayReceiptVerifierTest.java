package com.gamebuddy.billing.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.common.exception.BusinessException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The Play verifier, exercised against stubbed API responses.
 *
 * <p>What is tested here is the decision-making: which responses are accepted, which are
 * refused, and that nothing the client claims is believed over what Google says. The
 * network call and the OAuth exchange are stubbed — those need real credentials and a
 * sandbox purchase, and are the part that must be checked against Google before this is
 * switched on.
 */
class GooglePlayReceiptVerifierTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

    private HttpClient http;
    private GoogleServiceAccount account;
    private GooglePlayReceiptVerifier verifier;

    @BeforeEach
    void setUp() {
        http = mock(HttpClient.class);
        account = mock(GoogleServiceAccount.class);
        when(account.accessToken(any())).thenReturn("ya29.stub");
        verifier = new GooglePlayReceiptVerifier("com.gamebuddy.app", account, http, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        // Raw doReturn: send() is generic in its BodyHandler, so the inferred return type
        // does not match HttpResponse<String> at the call site.
        doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void platformIsGooglePlay() {
        assertEquals(PurchasePlatform.GOOGLE_PLAY, verifier.platform());
    }

    // -- subscriptions -------------------------------------------------------

    @Test
    @DisplayName("an active subscription is accepted, with the expiry Google reports")
    void activeSubscriptionIsAccepted() throws Exception {
        respond(200, """
                {
                  "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
                  "latestOrderId": "GPA.1234",
                  "startTime": "2026-08-01T00:00:00Z",
                  "lineItems": [
                    {"productId": "gamebuddy.gold.monthly", "expiryTime": "2026-09-01T00:00:00Z"}
                  ]
                }
                """);

        var result = verifier.verify("token-1", Product.GOLD_MONTHLY);

        assertEquals("GPA.1234", result.storeTransactionId());
        assertEquals(Product.GOLD_MONTHLY, result.product());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), result.expiresAt());
    }

    @Test
    @DisplayName("a grace period still counts: they paid, Google is retrying the card")
    void gracePeriodIsAccepted() throws Exception {
        respond(200, """
                {
                  "subscriptionState": "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
                  "latestOrderId": "GPA.5",
                  "lineItems": [{"productId": "gamebuddy.gold.monthly", "expiryTime": "2026-09-01T00:00:00Z"}]
                }
                """);

        assertDoesNotThrow(() -> verifier.verify("token-1", Product.GOLD_MONTHLY));
    }

    @Test
    @DisplayName("an expired or cancelled subscription is refused")
    void inactiveSubscriptionIsRefused() throws Exception {
        respond(200, """
                {
                  "subscriptionState": "SUBSCRIPTION_STATE_EXPIRED",
                  "lineItems": [{"productId": "gamebuddy.gold.monthly"}]
                }
                """);

        assertThrows(BusinessException.class, () -> verifier.verify("token-1", Product.GOLD_MONTHLY));
    }

    @Test
    @DisplayName("a token for one product cannot be redeemed as another")
    void mismatchedProductIsRefused() throws Exception {
        // Google says this token is for the cheap coin pack; the client claims a year of Gold.
        respond(200, """
                {
                  "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
                  "latestOrderId": "GPA.9",
                  "lineItems": [{"productId": "gamebuddy.coins.500", "expiryTime": "2026-09-01T00:00:00Z"}]
                }
                """);

        assertThrows(BusinessException.class, () -> verifier.verify("token-1", Product.GOLD_YEARLY));
    }

    // -- one-off products ----------------------------------------------------

    @Test
    void purchasedCoinPackIsAccepted() throws Exception {
        respond(200, """
                {"purchaseState": 0, "orderId": "GPA.77", "productId": "gamebuddy.coins.500",
                 "purchaseTimeMillis": "1785600000000"}
                """);

        var result = verifier.verify("token-1", Product.COINS_SMALL);

        assertEquals("GPA.77", result.storeTransactionId());
        assertNull(result.expiresAt(), "a consumable does not expire");
    }

    @Test
    @DisplayName("a pending or cancelled purchase grants nothing")
    void unpurchasedStateIsRefused() throws Exception {
        respond(200, """
                {"purchaseState": 2, "orderId": "GPA.77", "productId": "gamebuddy.coins.500"}
                """);

        assertThrows(BusinessException.class, () -> verifier.verify("token-1", Product.COINS_SMALL));
    }

    // -- failure modes -------------------------------------------------------

    @Test
    @DisplayName("a token Google does not recognise is refused")
    void unknownTokenIsRefused() throws Exception {
        respond(404, "{}");

        assertThrows(BusinessException.class, () -> verifier.verify("forged", Product.COINS_SMALL));
    }

    @Test
    @DisplayName("an API outage refuses rather than granting on an unreadable answer")
    void serverErrorIsRefused() throws Exception {
        respond(503, "");

        // Refusing is the only safe response: granting when the answer cannot be read is
        // how paid goods get handed out for free during an incident.
        assertThrows(BusinessException.class, () -> verifier.verify("token-1", Product.COINS_SMALL));
    }

    @Test
    @DisplayName("the request carries the OAuth token and targets the configured package")
    void requestIsAuthenticatedAndScoped() throws Exception {
        respond(200, """
                {"purchaseState": 0, "orderId": "GPA.1", "productId": "gamebuddy.coins.500"}
                """);

        verifier.verify("tok-abc", Product.COINS_SMALL);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any());
        HttpRequest request = captor.getValue();

        assertEquals(
                "Bearer ya29.stub",
                request.headers().firstValue("Authorization").orElse(""));
        String url = request.uri().toString();
        assertTrue(url.contains("com.gamebuddy.app"), "must scope to our package: " + url);
        assertTrue(url.contains("gamebuddy.coins.500"));
        assertTrue(url.contains("tok-abc"));
    }
}
