package com.gamebuddy.shared.funnel;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the client reports the funnel steps only it can see.
 *
 * <p>Opening a paywall and tapping a plan happen entirely on the device; the server never
 * learns about them unless it is told. Everything downstream — a granted subscription, a
 * spent coin — is already recorded server-side and is not reported here, because a number
 * the client asserts is a number the client can be wrong about.
 *
 * <p>The gamer comes from the authenticated principal, never the body. An endpoint that
 * accepted a user id would let anyone attribute funnel steps to anyone.
 *
 * <p><b>Failures are swallowed.</b> A dropped analytics event costs a fraction of a
 * percentage point on a dashboard; an analytics call that fails a user's action costs the
 * action. This always answers 200 for a recognised step, whatever happened underneath.
 */
@Slf4j
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class FunnelController {

    private final FunnelEventRepository events;
    private final Clock clock;

    @PostMapping("/funnel/{step}")
    public ResponseEntity<DefaultMessageResponse> record(
            @AuthenticationPrincipal Gamer principal, @PathVariable String step) {

        FunnelStep kind;
        try {
            kind = FunnelStep.valueOf(step.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            // Refused rather than stored. An unknown step is a client that has drifted from
            // the enum, and quietly accepting it would fill the table with names no query
            // knows about.
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "unknown funnel step");
        }

        try {
            events.save(new FunnelEvent(principal.getUserId(), kind, clock.instant()));
        } catch (RuntimeException e) {
            log.warn("Could not record funnel step {}", kind, e);
        }
        return ResponseEntity.ok(DefaultMessageResponse.of("Recorded"));
    }
}
