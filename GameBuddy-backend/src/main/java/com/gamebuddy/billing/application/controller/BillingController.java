package com.gamebuddy.billing.application.controller;

import com.gamebuddy.billing.domain.PurchaseService;
import com.gamebuddy.billing.infrastructure.entity.Purchase;
import com.gamebuddy.billing.interfaces.dto.PurchaseResponseBody;
import com.gamebuddy.billing.interfaces.dto.SubscriptionResponseBody;
import com.gamebuddy.billing.interfaces.request.RedeemPurchaseRequest;
import com.gamebuddy.billing.interfaces.response.PurchaseResponse;
import com.gamebuddy.billing.interfaces.response.SubscriptionResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Purchases and subscription state.
 *
 * <p>The gamer is taken from the authenticated principal, never from the request body — a
 * user id in the body would let anyone redeem a receipt onto somebody else's account, or
 * onto an account they had just created for the purpose.
 */
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final PurchaseService purchaseService;
    private final Clock clock;

    @PostMapping("/redeem")
    public ResponseEntity<PurchaseResponse> redeem(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody RedeemPurchaseRequest request) {

        Purchase purchase = purchaseService.redeem(
                principal.getUserId(), request.getPlatform(), request.getProductId(), request.getReceipt());

        PurchaseResponseBody body = new PurchaseResponseBody(
                purchase.getProductId(), purchase.getStatus().name(), purchase.getEntitlementExpiresAt());

        PurchaseResponse response = new PurchaseResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }

    /** What the gamer currently holds, derived rather than read straight off the row. */
    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> subscription(@AuthenticationPrincipal Gamer principal) {
        SubscriptionTier tier = SubscriptionTier.effective(
                principal.getSubscriptionTier(), principal.getSubscriptionExpiresAt(), clock.instant());

        SubscriptionResponseBody body = new SubscriptionResponseBody(
                tier.name(),
                tier == SubscriptionTier.BASIC ? null : principal.getSubscriptionExpiresAt(),
                tier.dailyAccepts(),
                tier.canSeeWhoLikedYou(),
                tier.canUseAdvancedFilters());

        SubscriptionResponse response = new SubscriptionResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }
}
