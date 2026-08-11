package com.gamebuddy.billing.application.controller;

import com.gamebuddy.billing.interfaces.dto.SubscriptionResponseBody;
import com.gamebuddy.billing.interfaces.response.SubscriptionResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * What this account currently holds.
 *
 * <p><b>Read-only, and that is the design.</b> There used to be a {@code POST /redeem} here
 * that took a store receipt from the client. Verification now belongs to RevenueCat, which
 * reports the outcome over a webhook, so the client has nothing to submit — and, more to
 * the point, must not be able to. An endpoint where the app asserts a purchase is a free
 * subscription for anybody willing to send the request themselves.
 *
 * <p>The gamer is taken from the authenticated principal, never from the request.
 */
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final Clock clock;

    /**
     * What the gamer currently holds, derived rather than read straight off the row.
     *
     * <p>This is what the app polls after a purchase. The store sheet closing and our
     * entitlement arriving are two different events — RevenueCat has to tell us in between
     * — so the client cannot assume the subscription is live the moment the sheet closes.
     */
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
