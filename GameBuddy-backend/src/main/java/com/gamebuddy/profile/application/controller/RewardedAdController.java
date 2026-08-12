package com.gamebuddy.profile.application.controller;

import com.gamebuddy.profile.domain.coin.RewardedAdService;
import com.gamebuddy.profile.domain.coin.RewardedAdVerifier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where AdMob tells us somebody finished watching an advert.
 *
 * <p><b>Public, unauthenticated, and it has to be.</b> Google's servers call this, not the
 * app, so there is no session and no bearer token to present. The ECDSA signature on the
 * query string is the only authentication, which makes {@link RewardedAdVerifier} the thing
 * standing between this endpoint and an unlimited coin faucet.
 *
 * <p><b>Everything answers 200.</b> A rejected callback, a replay, a capped gamer, an
 * account that no longer exists — all 200. AdMob retries anything else with backoff and
 * eventually disables the callback, which would take the working rewards down along with
 * the broken ones. The status code here means "received", never "granted"; what actually
 * happened goes to the log, where it can be read without teaching a prober which of their
 * forged signatures got closest.
 *
 * <p>A GET that changes state, which is normally wrong. It is AdMob's protocol rather than
 * our choice, and the duplicate defence in {@code RewardedAdService} is what makes it safe
 * to be retried — by their servers, by a browser prefetch, or by anyone replaying it.
 */
@Slf4j
@RestController
@RequestMapping("/ads")
@RequiredArgsConstructor
public class RewardedAdController {

    /** The parameter that terminates the signed portion of the query string. */
    private static final String SIGNATURE_MARKER = "&signature=";

    private final RewardedAdVerifier verifier;
    private final RewardedAdService rewards;

    @GetMapping("/reward")
    public ResponseEntity<String> reward(
            HttpServletRequest request,
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(name = "transaction_id", required = false) String transactionId,
            @RequestParam(name = "signature", required = false) String signature,
            @RequestParam(name = "key_id", required = false) String keyId) {

        // The raw query string, not the parsed parameters. Google signed these exact bytes
        // in this exact order, so anything that rebuilds the string from a map — even
        // faithfully — produces a different one and verifies nothing.
        String query = request.getQueryString();
        if (query == null || userId == null || transactionId == null) {
            log.debug("Rewarded-ad callback missing required parameters");
            return ok();
        }

        int cut = query.indexOf(SIGNATURE_MARKER);
        if (cut < 0) {
            // AdMob always appends signature and key_id last. Their absence means this is
            // not an AdMob callback.
            log.debug("Rewarded-ad callback carried no signature");
            return ok();
        }
        String signedContent = query.substring(0, cut);

        if (!verifier.verify(signedContent, signature, keyId)) {
            // Warn, not error: an unverified callback is either a probe or a key rotation
            // we have not picked up, and only the second is ours to fix. The user id is
            // logged because a burst of them against one account is the shape of somebody
            // trying to farm a specific profile.
            log.warn("Rewarded-ad callback failed verification for {}", userId);
            return ok();
        }

        RewardedAdService.Outcome outcome = rewards.grant(transactionId, userId);
        log.debug("Rewarded-ad callback for {}: {}", userId, outcome);
        return ok();
    }

    private ResponseEntity<String> ok() {
        // A bare 200 with no body. AdMob reads nothing but the status, and an error shape
        // here would only describe our internals to whoever is probing.
        return ResponseEntity.ok().build();
    }
}
