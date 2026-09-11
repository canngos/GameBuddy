package com.gamebuddy.billing.application.controller;

import com.gamebuddy.billing.domain.RevenueCatService;
import com.gamebuddy.billing.interfaces.request.RevenueCatWebhook;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where RevenueCat tells us somebody paid.
 *
 * <p><b>This is the only way an entitlement can be granted, so this shared secret is the
 * entire security boundary for billing.</b> With receipt verification gone, there is no
 * second opinion: anybody who can post a convincing body to this URL with the right header
 * can hand themselves a subscription. Hence a constant-time comparison, a refusal to start
 * without a configured secret, and no detail whatsoever in the rejection response.
 *
 * <p>The token is whatever is typed into the Authorization field of the webhook
 * configuration in the RevenueCat dashboard, and RevenueCat sends it verbatim in the
 * {@code Authorization} header. It is not a bearer token and is not parsed as one.
 *
 * <p><b>Always answers 200 once authorised.</b> RevenueCat retries any non-2xx with
 * backoff and eventually disables a webhook that keeps failing, so an event we cannot make
 * sense of is logged and accepted rather than rejected — one unknown product must not stop
 * delivery of everybody else's purchases. The cases where that loses information are all
 * logged at ERROR, which is where they belong: they mean somebody paid and did not get
 * what they paid for.
 */
@Slf4j
@RestController
@RequestMapping("/billing/revenuecat")
public class RevenueCatWebhookController {

    private final RevenueCatService revenueCat;
    private final byte[] expectedToken;

    public RevenueCatWebhookController(
            RevenueCatService revenueCat, @Value("${gamebuddy.billing.revenuecat.webhook-token:}") String token) {
        this.revenueCat = revenueCat;

        if (!StringUtils.hasText(token)) {
            // Refuses to start rather than accepting anything. An unset secret would not
            // fail closed on its own — an empty expected value would match an empty header
            // — so the only safe unconfigured state is not running at all.
            throw new IllegalStateException("REVENUECAT_WEBHOOK_TOKEN is not set. It is the only thing standing"
                    + " between an anonymous POST and a free subscription; billing cannot run without it.");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody RevenueCatWebhook payload) {

        if (!authorised(authorization)) {
            // No body, no message. Anything descriptive here is a hint to whoever is
            // guessing, and the only legitimate caller already knows the answer.
            log.warn("Rejected an unauthorised RevenueCat webhook call");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String eventId = payload.event() == null ? "(none)" : payload.event().id();
        try {
            revenueCat.handle(payload.event());
        } catch (TransientDataAccessException
                | DataAccessResourceFailureException
                | CannotCreateTransactionException e) {
            // The database, not the event, was what failed — a dropped connection, a lock
            // timeout, a pool that could not hand out a transaction. This is the one case
            // where a retry helps, so answer 503 and let RevenueCat send it again. The retry
            // is safe: PurchaseService is idempotent on (platform, storeTransactionId), so
            // the same event applied twice still grants once. (OptimisticLockingFailureException
            // is a TransientDataAccessException too, so a lost race retries as well.)
            log.warn(
                    "Deferring RevenueCat event {} after a transient data-access failure: {}", eventId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (RuntimeException e) {
            // Everything else is deterministic — a bad product mapping, a malformed event.
            // A 500/503 would make RevenueCat retry it forever and then give up on the
            // webhook entirely, taking every other event with it, so it is logged and
            // swallowed with a 200: the event is acknowledged, the fault is ours to chase.
            log.error("Failed to apply RevenueCat event {}", eventId, e);
        }
        return ResponseEntity.ok().build();
    }

    /** Constant-time, so the comparison cannot be used to recover the token byte by byte. */
    private boolean authorised(String authorization) {
        if (authorization == null) {
            return false;
        }
        return MessageDigest.isEqual(authorization.getBytes(StandardCharsets.UTF_8), expectedToken);
    }
}
