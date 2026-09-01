package com.gamebuddy.billing.application.controller;

import com.gamebuddy.billing.domain.PromoCodeService;
import com.gamebuddy.billing.interfaces.request.RedeemPromoCodeRequest;
import com.gamebuddy.billing.interfaces.response.MyPromoCodesResponse;
import com.gamebuddy.billing.interfaces.response.RedeemPromoCodeResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.shared.entity.Gamer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redeeming a promotion code.
 *
 * <p>The neighbouring {@code BillingController} records why there is no endpoint where the
 * app asserts a purchase: that would be a free subscription for anybody able to write a
 * POST. This one looks similar and is not the same thing. Nothing here is asserted by the
 * client — it sends a string, and whether that string exists, who it was for, how much it
 * is worth and whether there is any left are all decided here against rows an
 * administrator wrote. The most a forged request achieves is redeeming a coupon its sender
 * already had.
 */
@RestController
@RequestMapping("/billing/promo-codes")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Subscriptions, purchases and promotion codes")
public class PromoCodeController {

    private final PromoCodeService promoCodes;

    /** What is waiting for this account, and what it has already used. */
    @GetMapping
    public ResponseEntity<MyPromoCodesResponse> mine(@AuthenticationPrincipal Gamer principal) {
        MyPromoCodesResponse response = new MyPromoCodesResponse();
        response.setBody(new BaseBody<>(promoCodes.mine(principal.getUserId())));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/redeem")
    public ResponseEntity<RedeemPromoCodeResponse> redeem(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody RedeemPromoCodeRequest request) {
        RedeemPromoCodeResponse response = new RedeemPromoCodeResponse();
        response.setBody(new BaseBody<>(promoCodes.redeem(principal.getUserId(), request.getCode())));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }
}
