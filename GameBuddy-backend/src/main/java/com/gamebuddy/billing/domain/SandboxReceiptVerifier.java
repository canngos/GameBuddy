package com.gamebuddy.billing.domain;

import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A verifier for local development and tests. <strong>It verifies nothing.</strong>
 *
 * <p>It exists so the purchase flow can be exercised end to end before Apple and Google
 * credentials are available, and it is built to be impossible to enable by accident:
 *
 * <ul>
 *   <li>the bean only exists when {@code gamebuddy.billing.sandbox=true}, which defaults to
 *       false, so a missing property means no verifier rather than a permissive one;
 *   <li>{@code PurchaseService} refuses any platform it has no verifier for, so with this
 *       bean absent every purchase is rejected instead of silently granted;
 *   <li>it logs a warning on every startup and on every call, so an environment running it
 *       cannot look normal in the logs.
 * </ul>
 *
 * <p>Shipping this to production would mean anyone who can send an HTTP request can grant
 * themselves Gold and unlimited coins. Replace it with real {@code AppleReceiptVerifier}
 * and {@code GooglePlayReceiptVerifier} implementations before taking payments.
 */
@Slf4j
@RequiredArgsConstructor
public class SandboxReceiptVerifier implements ReceiptVerifier {

    private final PurchasePlatform platform;
    private final Clock clock;

    @PostConstruct
    void warnLoudly() {
        log.warn(
                "SANDBOX BILLING IS ENABLED for {}. Purchases are NOT verified against any store "
                        + "and anyone can grant themselves paid features. Never run this in production.",
                platform);
    }

    @Override
    public PurchasePlatform platform() {
        return platform;
    }

    @Override
    public VerifiedPurchase verify(String receipt, Product claimedProduct) {
        if (receipt == null || receipt.isBlank()) {
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }
        log.warn("Sandbox billing accepted an unverified receipt for {}", claimedProduct.storeId());

        Instant now = clock.instant();
        return new VerifiedPurchase(
                // Derived from the receipt so that replaying the same one is still caught
                // by the unique constraint, exactly as a real store id would be.
                "sandbox-" + Integer.toHexString(receipt.hashCode()),
                claimedProduct,
                now,
                claimedProduct.isSubscription() ? now.plus(claimedProduct.period()) : null);
    }
}
