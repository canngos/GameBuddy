package com.gamebuddy.billing.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies a Google Play purchase with the Android Publisher API.
 *
 * <p>The client sends the opaque {@code purchaseToken} it got from the Play Billing
 * Library. That token means nothing on its own — it is checked against Google, and only
 * Google's answer is believed. Nothing about what the client claims is trusted beyond
 * naming which product to look up, and even that is compared against the response.
 *
 * <p>Subscriptions and one-off products live at different endpoints and return different
 * shapes, so the product's own type decides which is called.
 */
@Slf4j
public class GooglePlayReceiptVerifier implements ReceiptVerifier {

    private static final String API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";

    /** {@code purchaseState} 0 means purchased. 1 is cancelled, 2 is pending. */
    private static final int PURCHASED = 0;

    /** Subscription state as reported by subscriptionsv2. */
    private static final String SUB_ACTIVE = "SUBSCRIPTION_STATE_ACTIVE";

    private static final String SUB_IN_GRACE = "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";

    private final String packageName;
    private final GoogleServiceAccount serviceAccount;
    private final HttpClient http;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param serviceAccountJson path to the Play Console service-account key
     */
    public GooglePlayReceiptVerifier(
            String packageName, java.nio.file.Path serviceAccountJson, HttpClient http, Clock clock) {
        this(packageName, new GoogleServiceAccount(serviceAccountJson, http), http, clock);
    }

    /** Package-private so tests can supply a service account without a key on disk. */
    GooglePlayReceiptVerifier(String packageName, GoogleServiceAccount serviceAccount, HttpClient http, Clock clock) {
        this.packageName = packageName;
        this.serviceAccount = serviceAccount;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public PurchasePlatform platform() {
        return PurchasePlatform.GOOGLE_PLAY;
    }

    @Override
    public VerifiedPurchase verify(String receipt, Product claimedProduct) {
        JsonNode body = get(endpointFor(claimedProduct, receipt));
        return claimedProduct.isSubscription()
                ? readSubscription(body, claimedProduct)
                : readOneOff(body, claimedProduct);
    }

    private String endpointFor(Product product, String purchaseToken) {
        String token = URLEncoder.encode(purchaseToken, StandardCharsets.UTF_8);
        String pkg = URLEncoder.encode(packageName, StandardCharsets.UTF_8);
        if (product.isSubscription()) {
            // v2: the v1 subscriptions endpoint is deprecated and reports less about state.
            return "%s/%s/purchases/subscriptionsv2/tokens/%s".formatted(API, pkg, token);
        }
        return "%s/%s/purchases/products/%s/tokens/%s"
                .formatted(API, pkg, URLEncoder.encode(product.storeId(), StandardCharsets.UTF_8), token);
    }

    private JsonNode get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + serviceAccount.accessToken(clock.instant()))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 410) {
                // Google does not know this token: forged, or from a different app.
                log.warn("Google Play does not recognise a purchase token (HTTP {})", response.statusCode());
                throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
            }
            if (response.statusCode() != 200) {
                // A 5xx or an auth problem is not the buyer's fault. Refusing is still the
                // only safe answer — granting on an unreadable response is how paid goods
                // get handed out for free — but it is logged as an outage, not a forgery.
                log.error("Android Publisher API returned HTTP {}", response.statusCode());
                throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            log.error("Could not reach the Android Publisher API", e);
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED, e);
        }
    }

    private VerifiedPurchase readSubscription(JsonNode body, Product product) {
        String state = body.path("subscriptionState").asText("");
        if (!SUB_ACTIVE.equals(state) && !SUB_IN_GRACE.equals(state)) {
            // Expired, cancelled, paused, on hold, or never started. A grace period still
            // counts: the user has paid and Google is retrying their payment method.
            log.warn("Google Play subscription is not active: {}", state);
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }

        JsonNode line = body.path("lineItems").path(0);
        String productId = line.path("productId").asText("");
        requireSameProduct(productId, product);

        return new VerifiedPurchase(
                // orderId identifies the transaction; latestOrderId is the current one for
                // a renewing subscription, which is what makes each renewal distinct.
                body.path("latestOrderId")
                        .asText(body.path("externalAccountIdentifiers").asText("")),
                product,
                parseTime(body.path("startTime").asText(null), clock.instant()),
                parseTime(line.path("expiryTime").asText(null), null));
    }

    private VerifiedPurchase readOneOff(JsonNode body, Product product) {
        int state = body.path("purchaseState").asInt(-1);
        if (state != PURCHASED) {
            log.warn("Google Play one-off purchase is not in the purchased state: {}", state);
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }
        requireSameProduct(body.path("productId").asText(product.storeId()), product);

        long millis = body.path("purchaseTimeMillis").asLong(0L);
        return new VerifiedPurchase(
                body.path("orderId").asText(""),
                product,
                millis > 0 ? Instant.ofEpochMilli(millis) : clock.instant(),
                // Consumables never expire; the coins are credited once.
                null);
    }

    /**
     * Refuses a response describing something other than what was claimed.
     *
     * <p>Without this a token for the cheapest coin pack could be redeemed as a yearly
     * subscription, because the endpoint for one-off products is told which product to look
     * up rather than discovering it.
     */
    private void requireSameProduct(String reported, Product claimed) {
        if (!reported.isEmpty() && !reported.equals(claimed.storeId())) {
            log.warn("Google Play reported product {} but {} was claimed", reported, claimed.storeId());
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }
    }

    private static Instant parseTime(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            // Silently substituting a fallback is how a subscription ends up with an
            // expiry nobody can explain. The substitution is still the right behaviour —
            // refusing the purchase over a timestamp would be worse — but it should not
            // also be invisible.
            log.warn("Google Play sent an unparseable timestamp '{}'; using {}", value, fallback);
            return fallback;
        }
    }
}
