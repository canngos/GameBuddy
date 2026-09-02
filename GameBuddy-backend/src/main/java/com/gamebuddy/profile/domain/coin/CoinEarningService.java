package com.gamebuddy.profile.domain.coin;

import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.domain.mission.MissionService;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paying out the coin faucets.
 *
 * <p><b>Every claim is read-check-write inside one transaction</b>, so it runs under the
 * {@code @Version} optimistic lock on {@link Gamer} — the same discipline
 * {@code DefaultCosmeticService.buy} uses for spending. It matters more here than there:
 * two taps of a claim button arriving together would otherwise both see "not claimed yet",
 * both pass the check, and pay twice for one day. A double-charge is a support ticket; a
 * double-payout is an exploit somebody will find and repeat.
 *
 * <p>Missions are not here. They were — three of them, as an enum nested in
 * {@link CoinFaucet} with their baselines spread across five columns on the gamer — and
 * they outgrew it: {@code MissionService} owns dealing, progress and claiming now, and this
 * class only asks it what to show and forwards a claim. Four faucets in one class was
 * already one too many.
 *
 * <p>{@link #state} is still not read-only, because asking for the earn screen can deal a
 * gamer their first set of missions, and a deal is a write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinEarningService {

    private final GamerRepository gamers;
    private final CoinFaucet faucet;
    private final MissionService missionService;
    private final Clock clock;
    private final CoinLedger coins;

    /** What a gamer can claim, and what they would get. */
    public record Earnings(
            boolean dailyAvailable,
            int dailyReward,
            int streak,
            Instant dailyReadyAt,
            MissionService.ActiveSet missions,
            boolean stipendAvailable,
            int stipendAmount,
            Instant stipendReadyAt,
            int coinBalance,
            /** Rewarded adverts this gamer may still be paid for today. */
            int adsLeftToday,
            /**
             * The whole streak cycle, and what one advert pays.
             *
             * <p>Sent because the client used to hold its own copies of both — the strip
             * drew a seven-day ladder from a constant in the app, and the advert row
             * printed another. Retuning the economy then needed a store release to stop
             * the UI lying about it, which is most of the reason the rates were never
             * retuned at all.
             */
            List<Integer> dailyLadder,
            int adCoins) {}

    /**
     * The whole earn screen in one read.
     *
     * <p>Not {@code readOnly}: a gamer who has never been here has no missions, and the
     * first look is what deals them. Dealing lazily on read rather than from a job is what
     * keeps the mission table proportional to the people playing rather than to the people
     * registered.
     */
    @Transactional
    public Earnings state(Gamer principal) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();
        return snapshot(gamer, now);
    }

    /** Takes the daily coins. */
    @Transactional
    public Earnings claimDaily(Gamer principal) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();

        if (!faucet.dailyAvailable(gamer.getDailyClaimedAt(), now)) {
            throw new BusinessException(TransactionCode.REWARD_NOT_READY);
        }

        int streak = faucet.streakAfterClaim(gamer.getDailyStreak(), gamer.getDailyClaimedAt(), now);
        int reward = faucet.dailyReward(streak);

        gamer.setDailyStreak(streak);
        gamer.setDailyClaimedAt(now);
        // The streak forgets a broken run; this does not. It is the only counter in the
        // app incremented on a write rather than derived on read, and it is here because
        // there is no row anywhere that says "this gamer turned up on this day".
        gamer.setDailyClaimsTotal(gamer.getDailyClaimsTotal() + 1);
        coins.earn(gamer, reward, CoinReason.DAILY_STREAK);

        gamers.save(gamer);

        log.info("Daily {} coins to {} (streak {})", reward, gamer.getUserId(), streak);
        return snapshot(gamer, now);
    }

    /**
     * Takes a finished mission's coins.
     *
     * <p>A pass-through: the rule about what is finished, what it pays and what comes next
     * belongs to {@code MissionService}, and this method exists so that every faucet still
     * answers with the same whole-screen snapshot.
     */
    @Transactional
    public Earnings claimMission(Gamer principal, String code) {
        Gamer gamer = reload(principal);
        missionService.claim(gamer, code);
        gamers.save(gamer);
        return snapshot(gamer, clock.instant());
    }

    /** Takes the monthly Gold stipend. */
    @Transactional
    public Earnings claimStipend(Gamer principal) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();

        if (effectiveTier(gamer, now) != SubscriptionTier.GOLD) {
            throw new BusinessException(TransactionCode.SUBSCRIPTION_REQUIRED);
        }
        if (!faucet.stipendAvailable(gamer.getStipendClaimedAt(), now)) {
            throw new BusinessException(TransactionCode.REWARD_NOT_READY);
        }

        gamer.setStipendClaimedAt(now);
        coins.earn(gamer, faucet.stipend(), CoinReason.GOLD_STIPEND);

        gamers.save(gamer);

        log.info("Stipend {} coins to {}", faucet.stipend(), gamer.getUserId());
        return snapshot(gamer, now);
    }

    // -----------------------------------------------------------------------

    private Earnings snapshot(Gamer gamer, Instant now) {
        boolean gold = effectiveTier(gamer, now) == SubscriptionTier.GOLD;
        int streakIfClaimed = faucet.streakAfterClaim(gamer.getDailyStreak(), gamer.getDailyClaimedAt(), now);

        return new Earnings(
                faucet.dailyAvailable(gamer.getDailyClaimedAt(), now),
                faucet.dailyReward(streakIfClaimed),
                gamer.getDailyStreak(),
                gamer.getDailyClaimedAt() == null
                        ? null
                        : gamer.getDailyClaimedAt().plus(faucet.dailyCooldown()),
                missionService.current(gamer),
                gold && faucet.stipendAvailable(gamer.getStipendClaimedAt(), now),
                faucet.stipend(),
                gold ? faucet.nextStipendAt(gamer.getStipendClaimedAt(), now) : null,
                gamer.getCoin(),
                faucet.rewardedAdsLeft(gamer.getRewardedAdsToday(), gamer.getRewardedAdDay(), now),
                faucet.dailyLadder(),
                faucet.rewardedAdCoins());
    }

    private SubscriptionTier effectiveTier(Gamer gamer, Instant now) {
        return SubscriptionTier.effective(gamer.getSubscriptionTier(), gamer.getSubscriptionExpiresAt(), now);
    }

    /**
     * Re-reads inside the transaction.
     *
     * <p>The principal comes from the security filter and was loaded in another one, so
     * writing to it would write a stale row — and for a balance, "stale" means paying out
     * against a number that has already moved.
     */
    private Gamer reload(Gamer principal) {
        return gamers.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }
}
