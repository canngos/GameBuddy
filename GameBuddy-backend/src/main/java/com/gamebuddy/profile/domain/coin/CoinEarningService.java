package com.gamebuddy.profile.domain.coin;

import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.domain.coin.CoinFaucet.Quest;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * <p>The week's quest baselines are also written by claims and by reads. That is why
 * {@link #state} is not read-only: the first look at the screen in a new week is what
 * records where the week started from, and it has to be recorded before any progress is
 * measured against it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinEarningService {

    private final GamerRepository gamers;
    private final List<BadgeMetricSource> metricSources;
    private final Clock clock;
    private final CoinLedger coins;

    /** What a gamer can claim, and what they would get. */
    public record Earnings(
            boolean dailyAvailable,
            int dailyReward,
            int streak,
            Instant dailyReadyAt,
            List<QuestProgress> quests,
            boolean stipendAvailable,
            int stipendAmount,
            Instant stipendReadyAt,
            int coinBalance,
            /** Rewarded adverts this gamer may still be paid for today. */
            int adsLeftToday) {}

    public record QuestProgress(Quest quest, int progress, boolean claimed) {}

    /**
     * The whole earn screen in one read.
     *
     * <p>Not {@code readOnly}: starting a new week has to persist the baselines, and doing
     * that lazily on read is what keeps a gamer who never opens this screen from
     * accumulating a "week" that began whenever they last claimed something.
     */
    @Transactional
    public Earnings state(Gamer principal) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();
        rollWeek(gamer, now);
        return snapshot(gamer, now);
    }

    /** Takes the daily coins. */
    @Transactional
    public Earnings claimDaily(Gamer principal) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();

        if (!CoinFaucet.dailyAvailable(gamer.getDailyClaimedAt(), now)) {
            throw new BusinessException(TransactionCode.REWARD_NOT_READY);
        }

        int streak = CoinFaucet.streakAfterClaim(gamer.getDailyStreak(), gamer.getDailyClaimedAt(), now);
        int reward = CoinFaucet.dailyReward(streak);

        gamer.setDailyStreak(streak);
        gamer.setDailyClaimedAt(now);
        coins.earn(gamer, reward, CoinReason.DAILY_STREAK);

        rollWeek(gamer, now);
        gamers.save(gamer);

        log.info("Daily {} coins to {} (streak {})", reward, gamer.getUserId(), streak);
        return snapshot(gamer, now);
    }

    /** Takes a finished quest's reward. */
    @Transactional
    public Earnings claimQuest(Gamer principal, Quest quest) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();
        rollWeek(gamer, now);

        if ((gamer.getQuestClaimedMask() & quest.bit()) != 0) {
            throw new BusinessException(TransactionCode.REWARD_NOT_READY);
        }
        if (progress(gamer, quest, measure(gamer)) < quest.target()) {
            throw new BusinessException(TransactionCode.QUEST_UNFINISHED);
        }

        gamer.setQuestClaimedMask(gamer.getQuestClaimedMask() | quest.bit());
        coins.earn(gamer, quest.reward(), CoinReason.WEEKLY_QUEST);
        gamers.save(gamer);

        log.info("Quest {} paid {} coins to {}", quest, quest.reward(), gamer.getUserId());
        return snapshot(gamer, now);
    }

    /** Takes the monthly Gold stipend. */
    @Transactional
    public Earnings claimStipend(Gamer principal) {
        Gamer gamer = reload(principal);
        Instant now = clock.instant();

        if (effectiveTier(gamer, now) != SubscriptionTier.GOLD) {
            throw new BusinessException(TransactionCode.SUBSCRIPTION_REQUIRED);
        }
        if (!CoinFaucet.stipendAvailable(gamer.getStipendClaimedAt(), now)) {
            throw new BusinessException(TransactionCode.REWARD_NOT_READY);
        }

        gamer.setStipendClaimedAt(now);
        coins.earn(gamer, CoinFaucet.STIPEND, CoinReason.GOLD_STIPEND);

        rollWeek(gamer, now);
        gamers.save(gamer);

        log.info("Stipend {} coins to {}", CoinFaucet.STIPEND, gamer.getUserId());
        return snapshot(gamer, now);
    }

    // -----------------------------------------------------------------------

    /**
     * Starts a new quest week if the old one has ended.
     *
     * <p>Records where the lifetime counters stood, so this week's progress can be measured
     * as a difference, and clears what was claimed. Called from every entry point rather
     * than a scheduled job: a job would have to walk every account weekly to reset counters
     * that only matter to gamers who actually turn up.
     */
    private void rollWeek(Gamer gamer, Instant now) {
        Instant weekStart = CoinFaucet.weekStart(now);
        if (weekStart.equals(gamer.getQuestWeekStartedAt())) {
            return;
        }

        Map<BadgeMetric, Integer> metrics = measure(gamer);
        gamer.setQuestWeekStartedAt(weekStart);
        gamer.setQuestBaseMessages(metrics.getOrDefault(BadgeMetric.MESSAGES_SENT, 0));
        gamer.setQuestBaseMatches(metrics.getOrDefault(BadgeMetric.MATCHES, 0));
        gamer.setQuestBasePosts(metrics.getOrDefault(BadgeMetric.POSTS_WRITTEN, 0));
        gamer.setQuestClaimedMask(0);
    }

    /**
     * How far into a quest this gamer is.
     *
     * <p>Clamped at zero. A lifetime total can fall below its baseline — a deleted post,
     * a match lost when the other account is removed — and a negative progress bar reads
     * as a bug rather than as the truth it is.
     */
    private int progress(Gamer gamer, Quest quest, Map<BadgeMetric, Integer> metrics) {
        int current = metrics.getOrDefault(quest.metric(), 0);
        int base =
                switch (quest) {
                    case TALK -> gamer.getQuestBaseMessages();
                    case MEET -> gamer.getQuestBaseMatches();
                    case POST -> gamer.getQuestBasePosts();
                };
        return Math.max(0, current - base);
    }

    private Earnings snapshot(Gamer gamer, Instant now) {
        Map<BadgeMetric, Integer> metrics = measure(gamer);

        List<QuestProgress> quests = java.util.Arrays.stream(Quest.values())
                .map(q -> new QuestProgress(
                        q,
                        Math.min(progress(gamer, q, metrics), q.target()),
                        (gamer.getQuestClaimedMask() & q.bit()) != 0))
                .toList();

        boolean gold = effectiveTier(gamer, now) == SubscriptionTier.GOLD;
        int streakIfClaimed = CoinFaucet.streakAfterClaim(gamer.getDailyStreak(), gamer.getDailyClaimedAt(), now);

        return new Earnings(
                CoinFaucet.dailyAvailable(gamer.getDailyClaimedAt(), now),
                CoinFaucet.dailyReward(streakIfClaimed),
                gamer.getDailyStreak(),
                gamer.getDailyClaimedAt() == null
                        ? null
                        : gamer.getDailyClaimedAt().plus(CoinFaucet.DAILY_COOLDOWN),
                quests,
                gold && CoinFaucet.stipendAvailable(gamer.getStipendClaimedAt(), now),
                CoinFaucet.STIPEND,
                gold ? CoinFaucet.nextStipendAt(gamer.getStipendClaimedAt(), now) : null,
                gamer.getCoin(),
                CoinFaucet.rewardedAdsLeft(gamer.getRewardedAdsToday(), gamer.getRewardedAdDay(), now));
    }

    private Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> merged = new EnumMap<>(BadgeMetric.class);
        for (BadgeMetricSource source : metricSources) {
            source.measure(gamer).forEach((metric, value) -> merged.merge(metric, value, Integer::max));
        }
        return merged;
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
