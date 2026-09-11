package com.gamebuddy.billing.application.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.gamebuddy.billing.domain.RevenueCatService;
import com.gamebuddy.billing.interfaces.request.RevenueCatWebhook;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * The webhook's answer decides whether RevenueCat retries.
 *
 * <p>A retry is only useful when the failure was transient — the database was briefly away —
 * and only safe because {@code PurchaseService} is idempotent. A deterministic failure that
 * retried would retry forever and then get the whole webhook disabled, taking every other
 * event with it. So transient means 503 and everything else means 200, and this is the test
 * that keeps the two apart. Plain unit test: the controller's only collaborators are the
 * service and the token.
 */
class RevenueCatWebhookControllerTest {

    private static final String TOKEN = "test-webhook-token";

    private RevenueCatWebhook.Event event() {
        return new RevenueCatWebhook.Event(
                "evt-1",
                "INITIAL_PURCHASE",
                "gamer-1",
                "coins_1000",
                1_786_500_000_000L,
                null,
                "PLAY_STORE",
                "txn-1",
                "txn-1",
                List.of(),
                null,
                "NORMAL",
                null,
                null);
    }

    private RevenueCatWebhookController controller(RevenueCatService service) {
        return new RevenueCatWebhookController(service, TOKEN);
    }

    @Test
    @DisplayName("a dropped database connection is deferred with 503 so RevenueCat retries")
    void transientDataFailureDefers() {
        RevenueCatService service = mock(RevenueCatService.class);
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(service)
                .handle(org.mockito.ArgumentMatchers.any());

        var response = controller(service).receive(TOKEN, new RevenueCatWebhook("1.0", event()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    @DisplayName("a pool that cannot open a transaction is also deferred")
    void cannotCreateTransactionDefers() {
        RevenueCatService service = mock(RevenueCatService.class);
        doThrow(new CannotCreateTransactionException("no connection"))
                .when(service)
                .handle(org.mockito.ArgumentMatchers.any());

        var response = controller(service).receive(TOKEN, new RevenueCatWebhook("1.0", event()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    @DisplayName("a deterministic failure is acknowledged with 200 so it is not retried forever")
    void deterministicFailureIsSwallowed() {
        RevenueCatService service = mock(RevenueCatService.class);
        doThrow(new IllegalStateException("unknown product")).when(service).handle(org.mockito.ArgumentMatchers.any());

        var response = controller(service).receive(TOKEN, new RevenueCatWebhook("1.0", event()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("the wrong token is refused before the event is looked at")
    void wrongTokenIsUnauthorised() {
        RevenueCatService service = mock(RevenueCatService.class);

        var response = controller(service).receive("not-the-token", new RevenueCatWebhook("1.0", event()));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("a missing Authorization header is refused")
    void missingTokenIsUnauthorised() {
        RevenueCatService service = mock(RevenueCatService.class);

        var response = controller(service).receive(null, new RevenueCatWebhook("1.0", event()));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // Referenced so a reader sees which header name the receive() parameter maps to.
    @SuppressWarnings("unused")
    private static final String AUTH = HttpHeaders.AUTHORIZATION;
}
