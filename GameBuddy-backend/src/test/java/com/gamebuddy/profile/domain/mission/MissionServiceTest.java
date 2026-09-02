package com.gamebuddy.profile.domain.mission;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.domain.coin.CoinEconomyProperties;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.badge.GamerMetrics;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinLedgerRepository;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerMission;
import com.gamebuddy.shared.repository.GamerMissionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The dealer.
 *
 * <p>Built over an in-memory stand-in for {@code gamer_mission} rather than a mock with
 * stubbed returns, because almost everything worth asserting here is about what happens
 * across several deals — that the campaign uses the whole pool exactly once, that the band
 * changes where it should, that the rate falls at the end. A mock that returns a fixed list
 * cannot answer any of those, and stubbing it per set would be writing the expected answer
 * into the test.
 */
@DisplayName("MissionService")
class MissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    private MissionService service;
    private Gamer gamer;

    /** The table. Keyed the way the real primary key is. */
    private final List<GamerMission> table = new ArrayList<>();

    /** What every metric currently reads. Tests move these to "play the game". */
    private final Map<BadgeMetric, Integer> metrics = new EnumMap<>(BadgeMetric.class);

    private final CoinEconomyProperties economy = new CoinEconomyProperties();

    @BeforeEach
    void setUp() {
        table.clear();
        metrics.clear();

        gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setCoin(0);

        BadgeMetricSource source = g -> new EnumMap<>(metrics);

        service = new MissionService(
                new FakeMissionRepository(),
                new GamerMetrics(List.of(source)),
                new CoinLedger(mock(CoinLedgerRepository.class), Clock.fixed(NOW, ZoneOffset.UTC)),
                economy,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // =======================================================================

    @Nested
    @DisplayName("dealing")
    class Dealing {

        @Test
        @DisplayName("a new gamer is dealt three missions from the easy band")
        void firstDeal() {
            MissionService.ActiveSet set = service.current(gamer);

            assertEquals(1, set.setIndex());
            assertEquals(MissionBand.EASY, set.band());
            assertFalse(set.veteran());
            assertEquals(Mission.PER_SET, set.missions().size());
            assertTrue(set.missions().stream().allMatch(m -> m.mission().getBand() == MissionBand.EASY));
            assertEquals(1, gamer.getMissionSetIndex());
        }

        @Test
        @DisplayName("the three are distinct, and they keep their slots between reads")
        void slotsAreStable() {
            MissionService.ActiveSet first = service.current(gamer);
            MissionService.ActiveSet again = service.current(gamer);

            assertEquals(
                    Mission.PER_SET,
                    first.missions().stream()
                            .map(m -> m.mission().getCode())
                            .distinct()
                            .count());
            assertEquals(codes(first), codes(again), "reading the screen twice must not reshuffle it");
            assertEquals(
                    List.of((short) 0, (short) 1, (short) 2),
                    first.missions().stream().map(MissionService.Progress::slot).toList());
        }

        @Test
        @DisplayName("progress is measured from where the gamer stood when the set was dealt")
        void baselineIsSnapshotAtDealTime() {
            // A busy account arriving at missions for the first time. Nothing it did before
            // being dealt the set may count towards it, or half the campaign arrives
            // pre-finished for anybody who has been here a while.
            for (BadgeMetric metric : BadgeMetric.values()) {
                metrics.put(metric, 40);
            }
            MissionService.ActiveSet dealt = service.current(gamer);

            assertTrue(
                    dealt.missions().stream().allMatch(m -> m.progress() == 0),
                    "history before the deal must not count towards it");

            // Now do five more of whatever the first mission happens to measure. Which
            // three were dealt is a shuffle, so the test reads the deal rather than
            // assuming it.
            MissionService.Progress first = dealt.missions().get(0);
            metrics.merge(first.mission().getMetric(), 5, Integer::sum);

            // Capped at the target, because some of the easy missions ask for fewer than
            // five of a thing — "send a super like" is finished at one.
            assertEquals(Math.min(5, first.mission().getTarget()), reread(first).progress());
        }

        @Test
        @DisplayName("progress never goes negative, and never exceeds the target")
        void progressIsClamped() {
            for (BadgeMetric metric : BadgeMetric.values()) {
                metrics.put(metric, 100);
            }
            MissionService.Progress first = service.current(gamer).missions().get(0);

            // The metric falls below its baseline — a match lost when the other account is
            // deleted, a lobby chat archived out from under a mission.
            metrics.put(first.mission().getMetric(), 10);
            assertTrue(service.current(gamer).missions().stream().allMatch(m -> m.progress() >= 0));

            metrics.put(first.mission().getMetric(), 100_000);
            assertEquals(
                    first.mission().getTarget(),
                    reread(first).progress(),
                    "a bar reading 99900/10 looks broken rather than proud");
        }
    }

    @Nested
    @DisplayName("the campaign")
    class Campaign {

        @Test
        @DisplayName("eight sets deal all twenty-four missions, each exactly once")
        void wholePoolDealtOnce() {
            Set<String> seen = new HashSet<>();

            for (int set = 1; set <= Mission.SETS; set++) {
                MissionService.ActiveSet active = service.current(gamer);
                assertEquals(set, active.setIndex());
                for (MissionService.Progress p : active.missions()) {
                    assertTrue(
                            seen.add(p.mission().getCode()),
                            p.mission().getCode() + " was dealt twice during the campaign");
                }
                finishSet(active);
            }

            assertEquals(Mission.values().length, seen.size(), "the campaign must use the whole pool");
        }

        @Test
        @DisplayName("the band rises at set four and again at set seven")
        void bandsEscalate() {
            List<MissionBand> bands = new ArrayList<>();
            for (int set = 1; set <= Mission.SETS; set++) {
                MissionService.ActiveSet active = service.current(gamer);
                bands.add(active.band());
                assertTrue(
                        active.missions().stream().allMatch(m -> m.mission().getBand() == active.band()),
                        "a set must not mix bands");
                finishSet(active);
            }

            assertEquals(
                    List.of(
                            MissionBand.EASY,
                            MissionBand.EASY,
                            MissionBand.EASY,
                            MissionBand.MEDIUM,
                            MissionBand.MEDIUM,
                            MissionBand.MEDIUM,
                            MissionBand.HARD,
                            MissionBand.HARD),
                    bands);
        }

        @Test
        @DisplayName("the reward rises with the band, and is frozen onto the row when dealt")
        void rewardsEscalate() {
            assertEquals(economy.getMissionRewards().getEasy(), rewardOfCurrentSet());
            finishSets(3);
            assertEquals(economy.getMissionRewards().getMedium(), rewardOfCurrentSet());
            finishSets(3);
            assertEquals(economy.getMissionRewards().getHard(), rewardOfCurrentSet());
        }

        @Test
        @DisplayName("a rate change does not move the price of a mission already on screen")
        void rewardIsFrozenAtDealTime() {
            int dealt = rewardOfCurrentSet();

            economy.getMissionRewards().setEasy(dealt + 999);

            assertEquals(dealt, rewardOfCurrentSet(), "the row carries its price; the config does not");
        }
    }

    @Nested
    @DisplayName("the veteran loop")
    class Veteran {

        @Test
        @DisplayName("the ninth set is where the campaign stops and the pay falls back")
        void payFallsBackAfterTheCampaign() {
            finishSets(Mission.SETS);

            MissionService.ActiveSet active = service.current(gamer);
            assertEquals(Mission.SETS + 1, active.setIndex());
            assertTrue(active.veteran());
            assertEquals(MissionBand.HARD, active.band(), "the work stays hard");
            assertEquals(
                    economy.getMissionRewards().getVeteran(),
                    active.missions().get(0).reward(),
                    "the pay does not");
        }

        @Test
        @DisplayName("it keeps dealing forever, and never repeats the set just finished")
        void neverRepeatsBackToBack() {
            finishSets(Mission.SETS);

            Set<String> previous = null;
            for (int i = 0; i < 12; i++) {
                MissionService.ActiveSet active = service.current(gamer);
                Set<String> now = new HashSet<>(codes(active));

                assertEquals(Mission.PER_SET, now.size(), "the three must be distinct");
                if (previous != null) {
                    assertTrue(
                            java.util.Collections.disjoint(previous, now),
                            "a veteran set must not repeat the one before it: " + previous + " then " + now);
                }
                previous = now;
                finishSet(active);
            }
        }

        @Test
        @DisplayName("a veteran set is worth less than the campaign's last set was")
        void grindingEarnsLessThanFinishing() {
            finishSets(Mission.SETS - 1);
            int lastCampaignSet = rewardOfCurrentSet();
            finishSets(1);
            int veteranSet = rewardOfCurrentSet();

            assertTrue(
                    veteranSet < lastCampaignSet,
                    "if grinding paid as well as finishing, the ladder would never actually end");
        }
    }

    @Nested
    @DisplayName("claiming")
    class Claiming {

        @Test
        @DisplayName("an unfinished mission pays nothing")
        void unfinishedIsRefused() {
            MissionService.ActiveSet active = service.current(gamer);
            String code = active.missions().get(0).mission().getCode();

            assertThrows(BusinessException.class, () -> service.claim(gamer, code));
            assertEquals(0, gamer.getCoin());
        }

        @Test
        @DisplayName("a finished mission pays what the row says, once")
        void paysOnce() {
            MissionService.ActiveSet active = service.current(gamer);
            MissionService.Progress first = active.missions().get(0);
            complete(first);

            service.claim(gamer, first.mission().getCode());
            assertEquals(first.reward(), gamer.getCoin());

            assertThrows(
                    BusinessException.class,
                    () -> service.claim(gamer, first.mission().getCode()));
            assertEquals(first.reward(), gamer.getCoin(), "the second tap must not pay again");
        }

        @Test
        @DisplayName("a mission that is not one of the three is refused")
        void foreignMissionIsRefused() {
            MissionService.ActiveSet active = service.current(gamer);
            Set<String> mine = new HashSet<>(codes(active));
            String notMine = java.util.Arrays.stream(Mission.values())
                    .map(Mission::getCode)
                    .filter(c -> !mine.contains(c))
                    .findFirst()
                    .orElseThrow();

            assertThrows(BusinessException.class, () -> service.claim(gamer, notMine));
        }

        @Test
        @DisplayName("an unknown code is refused rather than crashing")
        void unknownCodeIsRefused() {
            service.current(gamer);
            assertThrows(BusinessException.class, () -> service.claim(gamer, "no-such-mission"));
        }

        @Test
        @DisplayName("claiming the third of three deals the next three in the same call")
        void finishingASetDealsAgain() {
            MissionService.ActiveSet active = service.current(gamer);
            List<String> before = codes(active);

            for (int i = 0; i < Mission.PER_SET; i++) {
                complete(service.current(gamer).missions().get(i));
            }

            MissionService.ActiveSet returned = null;
            for (String code : before) {
                returned = service.claim(gamer, code);
            }

            assertNotNull(returned);
            assertEquals(2, returned.setIndex(), "the response to the last claim carries the new set");
            assertTrue(
                    java.util.Collections.disjoint(before, codes(returned)),
                    "the new three must not be the three just finished");
            assertTrue(returned.missions().stream().noneMatch(MissionService.Progress::claimed));
        }
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private List<String> codes(MissionService.ActiveSet set) {
        return set.missions().stream().map(m -> m.mission().getCode()).toList();
    }

    /** The same dealt mission, read again after the metrics have moved. */
    private MissionService.Progress reread(MissionService.Progress dealt) {
        return service.current(gamer).missions().stream()
                .filter(m -> m.slot() == dealt.slot())
                .findFirst()
                .orElseThrow();
    }

    private int rewardOfCurrentSet() {
        return service.current(gamer).missions().get(0).reward();
    }

    /** Moves the metric far enough past its baseline that this mission is finished. */
    private void complete(MissionService.Progress p) {
        BadgeMetric metric = p.mission().getMetric();
        metrics.merge(metric, p.mission().getTarget(), Integer::sum);
    }

    /** Finishes and claims every mission in a set, which deals the next one. */
    private void finishSet(MissionService.ActiveSet set) {
        for (MissionService.Progress p : set.missions()) {
            complete(p);
        }
        for (String code : codes(set)) {
            service.claim(gamer, code);
        }
    }

    private void finishSets(int count) {
        for (int i = 0; i < count; i++) {
            finishSet(service.current(gamer));
        }
    }

    /**
     * An in-memory {@code gamer_mission}.
     *
     * <p>Only the six methods the service actually calls are implemented; the rest of
     * {@code JpaRepository} throws, so a new call site shows up as a failure here rather
     * than as a silent null.
     */
    private class FakeMissionRepository implements GamerMissionRepository {

        @Override
        public List<GamerMission> findAllByUserIdAndSetIndexOrderBySlotAsc(String userId, int setIndex) {
            return table.stream()
                    .filter(m -> m.getUserId().equals(userId) && m.getSetIndex() == setIndex)
                    .sorted(java.util.Comparator.comparing(GamerMission::getSlot))
                    .toList();
        }

        @Override
        public List<String> findCodesDealtAfter(String userId, int after) {
            return table.stream()
                    .filter(m -> m.getUserId().equals(userId) && m.getSetIndex() > after)
                    .map(GamerMission::getMissionCode)
                    .toList();
        }

        @Override
        public List<String> findAllCodesDealt(String userId) {
            return table.stream()
                    .filter(m -> m.getUserId().equals(userId))
                    .map(GamerMission::getMissionCode)
                    .distinct()
                    .toList();
        }

        @Override
        public int claim(String userId, int setIndex, short slot, Instant now) {
            for (GamerMission m : table) {
                if (m.getUserId().equals(userId)
                        && m.getSetIndex() == setIndex
                        && m.getSlot() == slot
                        && m.getClaimedAt() == null) {
                    m.setClaimedAt(now);
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public void deleteAllByUserId(String userId) {
            table.removeIf(m -> m.getUserId().equals(userId));
        }

        @Override
        public <S extends GamerMission> List<S> saveAll(Iterable<S> entities) {
            List<S> saved = new ArrayList<>();
            for (S entity : entities) {
                table.add(entity);
                saved.add(entity);
            }
            return saved;
        }

        // --- everything else is not used, and should fail loudly if it becomes used ---

        @Override
        public <S extends GamerMission> S save(S entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<GamerMission> findById(GamerMission.Key key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(GamerMission.Key key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GamerMission> findAll() {
            return List.copyOf(table);
        }

        @Override
        public List<GamerMission> findAllById(Iterable<GamerMission.Key> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            return table.size();
        }

        @Override
        public void deleteById(GamerMission.Key key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(GamerMission entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllById(Iterable<? extends GamerMission.Key> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll(Iterable<? extends GamerMission> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll() {
            table.clear();
        }

        @Override
        public List<GamerMission> findAll(org.springframework.data.domain.Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public org.springframework.data.domain.Page<GamerMission> findAll(
                org.springframework.data.domain.Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void flush() {
            /* nothing buffered */
        }

        @Override
        public <S extends GamerMission> S saveAndFlush(S entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends GamerMission> List<S> saveAllAndFlush(Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(Iterable<GamerMission> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<GamerMission.Key> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllInBatch() {
            table.clear();
        }

        @Override
        public GamerMission getOne(GamerMission.Key key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GamerMission getById(GamerMission.Key key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GamerMission getReferenceById(GamerMission.Key key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends GamerMission> java.util.Optional<S> findOne(
                org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends GamerMission> List<S> findAll(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends GamerMission> List<S> findAll(
                org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends GamerMission> org.springframework.data.domain.Page<S> findAll(
                org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends GamerMission> long count(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends GamerMission> boolean exists(org.springframework.data.domain.Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends GamerMission, R> R findBy(
                org.springframework.data.domain.Example<S> example,
                java.util.function.Function<
                                org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>
                        queryFunction) {
            throw new UnsupportedOperationException();
        }
    }
}
