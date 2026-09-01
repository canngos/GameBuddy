package com.gamebuddy.billing.application.controller;

import com.gamebuddy.billing.domain.PromoCodeService;
import com.gamebuddy.billing.infrastructure.entity.PromoCode;
import com.gamebuddy.billing.interfaces.dto.PromoCodeDto;
import com.gamebuddy.billing.interfaces.dto.PromoCodeListResponseBody;
import com.gamebuddy.billing.interfaces.request.PromoCodeRequest;
import com.gamebuddy.billing.interfaces.response.PromoCodeListResponse;
import com.gamebuddy.billing.interfaces.response.PromoCodeResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issuing promotion codes.
 *
 * <p>Under {@code /admin} but owned by billing, the same arrangement as
 * {@code /admin/avatars} living in profile: the path is what the console calls, the module
 * is where the data belongs. Codes grant entitlements, and the rule for extending a
 * membership is in this module and must not be copied out of it.
 *
 * <p>Guarded twice, deliberately — {@code SecurityConfig} maps {@code /admin/**} to the
 * ADMIN role, and the annotation below says so again. One of them is the belt.
 *
 * <p>The email send sits here rather than inside the service transaction. A relay that
 * hangs would otherwise hold a write transaction open for its whole timeout, and a relay
 * that fails would roll back a code that was created perfectly well — so the code is
 * committed first and announced second, and the response says how many messages actually
 * left.
 */
@RestController
@RequestMapping("/admin/promo-codes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "User moderation and catalogue management")
public class PromoCodeAdminController {

    private final PromoCodeService promoCodes;

    @GetMapping
    public ResponseEntity<PromoCodeListResponse> list() {
        PromoCodeListResponse response = new PromoCodeListResponse();
        response.setBody(new BaseBody<>(new PromoCodeListResponseBody(promoCodes.list())));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromoCodeResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(single(promoCodes.get(id)));
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponse> create(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody PromoCodeRequest request) {
        PromoCode created = promoCodes.create(principal, request);
        return ResponseEntity.ok(single(promoCodes.detail(created.getId(), send(created, request))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromoCodeResponse> update(
            @PathVariable UUID id, @Valid @RequestBody PromoCodeRequest request) {
        PromoCode updated = promoCodes.update(id, request);
        return ResponseEntity.ok(single(promoCodes.detail(id, send(updated, request))));
    }

    /** Reversible. The rows all stay, and {@code /enable} puts it back. */
    @PostMapping("/{id}/disable")
    public ResponseEntity<DefaultMessageResponse> disable(@PathVariable UUID id) {
        PromoCode code = promoCodes.setDisabled(id, true);
        return ResponseEntity.ok(DefaultMessageResponse.of(code.getCode() + " can no longer be redeemed."));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<DefaultMessageResponse> enable(@PathVariable UUID id) {
        PromoCode code = promoCodes.setDisabled(id, false);
        return ResponseEntity.ok(DefaultMessageResponse.of(code.getCode() + " can be redeemed again."));
    }

    /**
     * Removes the code, its recipient list and its redemption records.
     *
     * <p>Not the same button as disable and not a replacement for it. Nothing already
     * granted is taken back: coins are in the ledger and Gold is on the account.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<DefaultMessageResponse> delete(@PathVariable UUID id) {
        promoCodes.delete(id);
        return ResponseEntity.ok(DefaultMessageResponse.of("Promotion code deleted."));
    }

    private PromoCodeDto.EmailOutcome send(PromoCode code, PromoCodeRequest request) {
        return request.isSendEmail() ? promoCodes.sendPending(code.getId()) : new PromoCodeDto.EmailOutcome(0, 0);
    }

    private PromoCodeResponse single(PromoCodeDto code) {
        PromoCodeResponse response = new PromoCodeResponse();
        response.setBody(new BaseBody<>(code));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
