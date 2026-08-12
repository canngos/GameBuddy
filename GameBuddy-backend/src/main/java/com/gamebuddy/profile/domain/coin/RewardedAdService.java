package com.gamebuddy.profile.domain.coin;

import com.gamebuddy.profile.infrastructure.repository.RewardedAdGrantRepository;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays out a rewarded advert that AdMob has confirmed was watched.
 *
 * <p>Called only from {@code RewardedAdController}, and only after the signature has
 * verified. Nothing here re-checks the signature and nothing here should be reachable from
 * an authenticated endpoint: an app that could call this could mint coins.
 *
 * <p><b>The reward amount is ours, not AdMob's.</b> The callback carries a
 * {@code reward_amount} configured in the AdMob console, and it is deliberately ignored in
 * favour of {@link CoinFaucet#REWARDED_AD_COINS}. Two reasons: the economy is balanced in
 * one place rather than in a web form nobody reviews, and a console misconfiguration —
 * fat-fingering 20 into 2000 — cannot inflate the currency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewardedAdService {

    private final GamerRepository gamers;
    private final RewardedAdGrantRepository grants;
    private final CoinLedger coins;
    private final Clock clock;

    /** What happened, for the log line and the endpoint's answer. */
    public enum Outcome {
        /** Coins were paid. */
        GRANTED,
        /** A callback we have already honoured. Not an error. */
        DUPLICATE,
        /** Today's cap is already reached. Not an error either. */
        CAPPED,
        /** No such account, or one that has been deleted. */
        UNKNOWN_USER
    }

    /**
     * Grants the coins for one verified advert.
     *
     * <p>Every outcome is a 200 to AdMob, including the refusals — see the controller for
     * why. This returns which one happened so it can be logged and counted rather than
     * guessed at.
     */
    @Transactional
    public Outcome grant(String transactionId, String userId) {
        Optional<Gamer> found = gamers.findById(userId);
        if (found.isEmpty() || found.get().getDeletedAt() != null) {
            // The user_id came from the app via setServerSideVerificationOptions, so it is
            // attacker-controlled in the sense that it is not ours — but it is signed, so
            // the only way to get here is a genuine reward for an account that has since
            // been deleted. Nothing to pay, nothing wrong.
            log.info("Rewarded ad for unknown or deleted account {}", userId);
            return Outcome.UNKNOWN_USER;
        }
        Gamer gamer = found.get();
        Instant now = clock.instant();

        // Taken first, before any coins move: claiming the transaction id is the
        // idempotency mechanism, so it has to happen before the thing it protects. Granting
        // first and recording after leaves a window in which a retry pays twice.
        //
        // An insert that reports whether it inserted, rather than a save() whose exception
        // is caught — see RewardedAdGrantRepository#claim for why save() cannot work here
        // and what it cost to find that out.
        if (grants.claim(transactionId, userId, CoinFaucet.REWARDED_AD_COINS, now) == 0) {
            // The normal case for a retry, and the only thing that stops a replayed URL.
            log.debug("Rewarded ad {} already honoured", transactionId);
            return Outcome.DUPLICATE;
        }

        Instant today = CoinFaucet.adDay(now);
        int watched = today.equals(gamer.getRewardedAdDay()) ? gamer.getRewardedAdsToday() : 0;

        if (watched >= CoinFaucet.REWARDED_AD_DAILY_CAP) {
            // The row above stays: this transaction is now spent either way, so a retry of
            // it cannot come back tomorrow and be paid against a fresh allowance.
            log.info("Rewarded ad for {} refused, daily cap reached", userId);
            return Outcome.CAPPED;
        }

        gamer.setRewardedAdDay(today);
        gamer.setRewardedAdsToday(watched + 1);
        coins.earn(gamer, CoinFaucet.REWARDED_AD_COINS, CoinReason.REWARDED_AD);
        gamers.save(gamer);

        log.info(
                "Rewarded ad paid {} coins to {} ({} of {} today)",
                CoinFaucet.REWARDED_AD_COINS,
                userId,
                watched + 1,
                CoinFaucet.REWARDED_AD_DAILY_CAP);
        return Outcome.GRANTED;
    }

    /** How many more adverts this gamer may be paid for today. Drives the Earn screen. */
    @Transactional(readOnly = true)
    public int remainingToday(Gamer principal) {
        return CoinFaucet.rewardedAdsLeft(
                principal.getRewardedAdsToday(), principal.getRewardedAdDay(), clock.instant());
    }
}
