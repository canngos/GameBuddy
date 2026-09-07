package com.gamebuddy.billing.application.controller;

import com.gamebuddy.billing.domain.UpgradePromptService;
import com.gamebuddy.billing.interfaces.dto.SubscriptionResponseBody;
import com.gamebuddy.billing.interfaces.response.SubscriptionResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
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
    private final UpgradePromptService upgradePrompts;

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
                tier.canUseAdvancedFilters(),
                // The lobby gate is "is Gold", checked the same way the service checks it,
                // so the create button can route to the paywall before the round trip.
                tier == SubscriptionTier.GOLD,
                upgradePrompts.isDue(principal));

        SubscriptionResponse response = new SubscriptionResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }

    /**
     * The client reporting that it has actually put the day-3 prompt on screen.
     *
     * <p>A write, so it is a POST, and the only write left on this controller. It grants
     * nothing and takes nothing away — the worst somebody can do by calling it themselves
     * is decline an offer they were going to be made.
     *
     * <p>Always 200, including when the prompt was already marked or was never due. The
     * client calls this as a side effect of rendering; an error would give it something to
     * handle at a moment when there is nothing useful it could do.
     */
    @PostMapping("/upgrade-prompt/seen")
    public ResponseEntity<DefaultMessageResponse> upgradePromptSeen(@AuthenticationPrincipal Gamer principal) {
        upgradePrompts.markShown(principal.getUserId());
        return ResponseEntity.ok(DefaultMessageResponse.of("Recorded"));
    }
}
