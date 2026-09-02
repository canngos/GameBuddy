package com.gamebuddy.profile.domain.mission;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.domain.coin.CoinEconomyProperties;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.GamerMetrics;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerMission;
import com.gamebuddy.shared.repository.GamerMissionRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dealing missions, and paying for them.
 *
 * <p>Three on screen at a time. Finish all three and the next three arrive with the claim
 * that finished them — not on a schedule, and not on the next request. The old weekly
 * quests reset on Monday morning UTC for everybody, which meant somebody who finished on
 * Tuesday got five days of a screen with nothing on it, and the fix is simply to deal
 * again the moment there is nothing left.
 *
 * <p><strong>Nothing here is written when the gamer plays.</strong> A dealt row snapshots
 * the metric it cares about, and progress is that metric now minus the snapshot. So sending
 * a message costs the mission system nothing at all; the cost is paid on read, by the same
 * counting the badges screen was already doing. That is why there is no event listener, no
 * counter to keep in step, and no scheduled job to reset anything.
 *
 * @see Mission for why the campaign is finite
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionService {

    private final GamerMissionRepository missions;
    private final GamerMetrics metrics;
    private final CoinLedger coins;
    private final CoinEconomyProperties economy;
    private final Clock clock;

    /**
     * Shuffling the deal.
     *
     * <p>{@link SecureRandom} rather than {@code Random} not because anybody could predict
     * their way to a coin, but because a predictable deal makes two accounts created in the
     * same second see the same three missions — which looks broken long before anybody
     * works out it is deterministic.
     */
    private final SecureRandom random = new SecureRandom();

    /** One dealt mission, with how far into it the gamer is. */
    public record Progress(Mission mission, short slot, int progress, int reward, boolean claimed) {}

    /** The three on screen, and where they sit in the campaign. */
    public record ActiveSet(int setIndex, MissionBand band, boolean veteran, List<Progress> missions) {}

    /**
     * The three missions this gamer is on, dealing a fresh set if they need one.
     *
     * <p>Not read-only: a first visit, or a visit after finishing a set in another session,
     * has to write the new deal. Dealing lazily on read rather than from a job is the same
     * choice the streak makes — a job would have to walk every account in the database to
     * hand missions to the ones who never open the app.
     */
    @Transactional
    public ActiveSet current(Gamer gamer) {
        return describe(gamer, ensureSet(gamer));
    }

    /**
     * Takes a finished mission's coins, and deals again if that was the last of the three.
     *
     * @param code a {@link Mission#getCode()}. Unknown, or not one of the three on screen,
     *     is a 400 rather than a 500 — it is a client asking for something we are not
     *     offering, which is a request problem and not a server fault.
     */
    @Transactional
    public ActiveSet claim(Gamer gamer, String code) {
        List<GamerMission> set = ensureSet(gamer);
        Instant now = clock.instant();

        GamerMission row = set.stream()
                .filter(m -> m.getMissionCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(TransactionCode.INVALID_REQUEST, "not one of your missions"));

        Mission mission = Mission.byCode(code)
                .orElseThrow(() -> new BusinessException(TransactionCode.INVALID_REQUEST, "unknown mission"));

        if (row.isClaimed()) {
            throw new BusinessException(TransactionCode.REWARD_NOT_READY);
        }
        if (progressOf(row, mission, metrics.measure(gamer)) < mission.getTarget()) {
            throw new BusinessException(TransactionCode.QUEST_UNFINISHED);
        }

        // The conditional update is the claim. Two taps arriving together would both pass
        // the check above — the read and the write are not one operation — and both would
        // pay. Zero rows back means somebody else got there first, and the honest answer to
        // the second tap is that there is nothing left to take rather than an error.
        if (missions.claim(gamer.getUserId(), row.getSetIndex(), row.getSlot(), now) == 0) {
            throw new BusinessException(TransactionCode.REWARD_NOT_READY);
        }
        row.setClaimedAt(now);

        coins.earn(gamer, row.getReward(), CoinReason.WEEKLY_QUEST);
        log.info("Mission {} paid {} coins to {}", code, row.getReward(), gamer.getUserId());

        if (set.stream().allMatch(GamerMission::isClaimed)) {
            int finished = row.getSetIndex();
            set = deal(gamer, finished + 1);
            log.info("Gamer {} finished mission set {}", gamer.getUserId(), finished);
        }
        return describe(gamer, set);
    }

    // -----------------------------------------------------------------------

    /**
     * The current set, dealing one if there is none or the last is finished.
     *
     * <p>A set with no rows is treated as no set at all. That happens exactly once per
     * account — the cursor starts at zero — but it is also what makes this recover if a
     * deal is ever rolled back after the cursor moved.
     */
    private List<GamerMission> ensureSet(Gamer gamer) {
        int setIndex = gamer.getMissionSetIndex();
        if (setIndex >= 1) {
            List<GamerMission> rows = missions.findAllByUserIdAndSetIndexOrderBySlotAsc(gamer.getUserId(), setIndex);
            if (!rows.isEmpty() && !rows.stream().allMatch(GamerMission::isClaimed)) {
                return rows;
            }
        }
        return deal(gamer, setIndex + 1);
    }

    /**
     * Writes the next three.
     *
     * <p>The band comes from the set number, the pool from the band, and what is left of the
     * pool from what this gamer has already been dealt.
     */
    private List<GamerMission> deal(Gamer gamer, int setIndex) {
        MissionBand band = Mission.bandForSet(setIndex);
        List<Mission> candidates = candidatesFor(gamer, setIndex, band);
        Collections.shuffle(candidates, random);

        Map<BadgeMetric, Integer> measured = metrics.measure(gamer);
        int reward = rewardFor(setIndex);

        List<GamerMission> rows = new ArrayList<>(Mission.PER_SET);
        for (short slot = 0; slot < Mission.PER_SET; slot++) {
            Mission mission = candidates.get(slot);
            rows.add(new GamerMission(
                    gamer.getUserId(),
                    setIndex,
                    slot,
                    mission.getCode(),
                    measured.getOrDefault(mission.getMetric(), 0),
                    reward));
        }

        gamer.setMissionSetIndex(setIndex);
        return missions.saveAll(rows);
    }

    /**
     * Which missions may be dealt into this set.
     *
     * <p><strong>During the campaign, nothing repeats.</strong> Every mission is dealt
     * exactly once across the eight sets, so the filter is simply everything this gamer has
     * ever seen. Each band holds a whole number of sets, so this always leaves exactly
     * enough.
     *
     * <p><strong>After it, only the previous set is excluded.</strong> The veteran loop
     * deals from the six HARD missions forever, and excluding the last two sets would
     * exclude all six. One set back leaves three of six, which is the most variety six
     * missions can offer without handing somebody the same thing twice running.
     *
     * <p>The fallback to the whole pool is unreachable while the invariant test passes —
     * it is here so that a badly sized band degrades into repeats rather than into an
     * {@link IndexOutOfBoundsException} on the earn screen.
     */
    private List<Mission> candidatesFor(Gamer gamer, int setIndex, MissionBand band) {
        Set<String> seen = new HashSet<>(
                Mission.isVeteranSet(setIndex)
                        ? missions.findCodesDealtAfter(gamer.getUserId(), setIndex - 2)
                        : missions.findAllCodesDealt(gamer.getUserId()));

        List<Mission> pool = Mission.inBand(band);
        List<Mission> fresh = new ArrayList<>(
                pool.stream().filter(m -> !seen.contains(m.getCode())).toList());
        if (fresh.size() < Mission.PER_SET) {
            log.warn("Mission band {} ran dry at set {}; dealing with repeats", band, setIndex);
            return new ArrayList<>(pool);
        }
        return fresh;
    }

    /** What one mission of this set pays. Frozen onto the row, never re-read. */
    private int rewardFor(int setIndex) {
        CoinEconomyProperties.MissionRewards rates = economy.getMissionRewards();
        if (Mission.isVeteranSet(setIndex)) {
            return rates.getVeteran();
        }
        return switch (Mission.bandForSet(setIndex)) {
            case EASY -> rates.getEasy();
            case MEDIUM -> rates.getMedium();
            case HARD -> rates.getHard();
        };
    }

    /**
     * How far into a mission this gamer is.
     *
     * <p>Clamped at zero. A metric can fall below its baseline — a match lost when the other
     * account is deleted, a lobby chat archived — and a negative progress bar reads as a bug
     * rather than as the truth it is. This is also why {@code Mission} is restricted to
     * cumulative metrics: the clamp hides a wobble, not a metric that genuinely runs
     * backwards.
     */
    private int progressOf(GamerMission row, Mission mission, Map<BadgeMetric, Integer> measured) {
        return Math.max(0, measured.getOrDefault(mission.getMetric(), 0) - row.getBaseline());
    }

    private ActiveSet describe(Gamer gamer, List<GamerMission> rows) {
        Map<BadgeMetric, Integer> measured = metrics.measure(gamer);
        int setIndex = rows.isEmpty() ? gamer.getMissionSetIndex() : rows.get(0).getSetIndex();

        List<Progress> progress = rows.stream()
                .map(row -> {
                    // A row whose mission has been retired from the catalogue is skipped
                    // rather than rendered blank — the same rule Badge.byCode follows.
                    Mission mission = Mission.byCode(row.getMissionCode()).orElse(null);
                    if (mission == null) {
                        return null;
                    }
                    return new Progress(
                            mission,
                            row.getSlot(),
                            Math.min(progressOf(row, mission, measured), mission.getTarget()),
                            row.getReward(),
                            row.isClaimed());
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return new ActiveSet(setIndex, Mission.bandForSet(setIndex), Mission.isVeteranSet(setIndex), progress);
    }
}
