package com.gamebuddy.profile.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.domain.badge.Badge;
import com.gamebuddy.profile.interfaces.dto.BadgeDto;
import com.gamebuddy.profile.interfaces.dto.BadgesResponseBody;
import com.gamebuddy.profile.interfaces.dto.ShowcasedBadgeDto;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinLedgerRepository;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerBadge;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerBadgeRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultBadgeServiceTest {

    @Mock
    private GamerBadgeRepository badgeRepository;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private ObjectStorage storage;

    @Mock
    private ApplicationEventPublisher events;

    private DefaultBadgeService badgeService;

    /** What the fake metric sources report. Set per test. */
    private Map<BadgeMetric, Integer> metrics;

    /** The rows the repository pretends this gamer has. */
    private List<GamerBadge> rows;

    private Gamer gamer;

    /**
     * Built by hand rather than with {@code @InjectMocks}: the constructor takes a list of
     * metric sources, and the whole point of that list is that it is composed at runtime.
     */
    @BeforeEach
    void setUp() {
        gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setGamerUsername("me");
        gamer.setCoin(100);
        gamer.setFcmToken("fcm-me");

        metrics = new EnumMap<>(BadgeMetric.class);
        rows = new ArrayList<>();

        // Two sources, each owning different metrics — the real arrangement, where the
        // profile module measures some and other modules measure the rest.
        BadgeMetricSource one = g -> filtered(BadgeMetric.MATCHES, BadgeMetric.FRIENDS);
        BadgeMetricSource two = g -> filtered(BadgeMetric.MESSAGES_SENT, BadgeMetric.LOBBIES_JOINED);

        badgeService = new DefaultBadgeService(
                badgeRepository,
                gamerRepository,
                storage,
                events,
                List.of(one, two),
                // Real, over a mocked repository: the badge reward is a coin movement now,
                // and a stubbed ledger would pay nothing while the tests still passed.
                new CoinLedger(mock(CoinLedgerRepository.class), Clock.systemUTC()));

        when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
        when(gamerRepository.save(any(Gamer.class))).thenAnswer(i -> i.getArgument(0));
        when(badgeRepository.findAllByUserId(gamer.getUserId())).thenAnswer(i -> List.copyOf(rows));
        when(badgeRepository.save(any(GamerBadge.class))).thenAnswer(i -> i.getArgument(0));
        when(badgeRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(storage.publicUrl(anyString())).thenAnswer(i -> "https://cdn/" + i.getArgument(0));
    }

    private Map<BadgeMetric, Integer> filtered(BadgeMetric... owned) {
        Map<BadgeMetric, Integer> out = new EnumMap<>(BadgeMetric.class);
        for (BadgeMetric metric : owned) {
            if (metrics.containsKey(metric)) {
                out.put(metric, metrics.get(metric));
            }
        }
        return out;
    }

    /** Pretends this badge is already earned. */
    private GamerBadge earn(Badge badge) {
        GamerBadge row = new GamerBadge(gamer.getUserId(), badge.getCode());
        rows.add(row);
        when(badgeRepository.findById(
                        argThat(key -> key != null && badge.getCode().equals(key.getBadgeCode()))))
                .thenReturn(Optional.of(row));
        return row;
    }

    private BadgesResponseBody body(com.gamebuddy.profile.interfaces.response.BadgesResponse response) {
        return response.getBody().getData();
    }

    private BadgeDto find(BadgesResponseBody body, Badge badge) {
        return body.getBadges().stream()
                .filter(dto -> dto.getCode().equals(badge.getCode()))
                .findFirst()
                .orElseThrow();
    }

    // =======================================================================

    @Nested
    class TheBoard {

        @Test
        @DisplayName("every mission is listed, not only the earned ones")
        void testGetBadges_whenNothingEarned_ReturnsWholeCatalogue() {
            BadgesResponseBody body = body(badgeService.getBadges(gamer));

            assertEquals(Badge.values().length, body.getBadges().size());
            assertEquals(Badge.values().length, body.getTotal());
            assertEquals(0, body.getEarned());
            // The locked ones are the point of the screen; a list of things already done
            // gives a new gamer nothing to aim at.
            assertTrue(body.getBadges().stream().noneMatch(BadgeDto::isEarned));
        }

        @Test
        void testGetBadges_whenCalled_ReportsProgressTowardsEachTarget() {
            metrics.put(BadgeMetric.MATCHES, 2);

            BadgesResponseBody body = body(badgeService.getBadges(gamer));

            assertEquals(2, find(body, Badge.SQUAD_FORMING).getProgress());
            assertEquals(3, find(body, Badge.SQUAD_FORMING).getTarget());
        }

        @Test
        @DisplayName("progress is capped at the target, so nothing reads 47 of 10")
        void testGetBadges_whenPastTheTarget_CapsProgress() {
            metrics.put(BadgeMetric.MATCHES, 47);

            BadgesResponseBody body = body(badgeService.getBadges(gamer));

            assertEquals(10, find(body, Badge.FULL_PARTY).getProgress());
        }

        @Test
        @DisplayName("the icon is a resolved URL, not the stored object key")
        void testGetBadges_whenCalled_ResolvesIcons() {
            BadgesResponseBody body = body(badgeService.getBadges(gamer));

            assertEquals(
                    "https://cdn/badges/badge-first-contact.png",
                    find(body, Badge.FIRST_CONTACT).getIcon());
        }

        @Test
        void testGetBadges_whenCalled_ReportsBalanceAndSlots() {
            BadgesResponseBody body = body(badgeService.getBadges(gamer));

            assertEquals(100, body.getCoins());
            assertEquals(GamerBadge.SHOWCASE_SLOTS, body.getShowcaseSlots());
        }
    }

    @Nested
    class Awarding {

        @Test
        void testEvaluate_whenTargetReached_AwardsTheBadge() {
            metrics.put(BadgeMetric.MATCHES, 1);

            badgeService.evaluate(gamer);

            ArgumentCaptor<List<GamerBadge>> saved = ArgumentCaptor.captor();
            verify(badgeRepository).saveAll(saved.capture());
            assertTrue(saved.getValue().stream()
                    .anyMatch(row -> row.getBadgeCode().equals(Badge.FIRST_CONTACT.getCode())));
        }

        @Test
        @DisplayName("one metric can complete several missions at once")
        void testEvaluate_whenCountJumpsPastSeveralTargets_AwardsAllOfThem() {
            // The old design awarded from inside the write that moved the counter, and a
            // jump past a threshold missed it forever. Evaluating against the current value
            // cannot miss one.
            metrics.put(BadgeMetric.MATCHES, 12);

            badgeService.evaluate(gamer);

            ArgumentCaptor<List<GamerBadge>> saved = ArgumentCaptor.captor();
            verify(badgeRepository).saveAll(saved.capture());
            List<String> codes =
                    saved.getValue().stream().map(GamerBadge::getBadgeCode).toList();
            assertTrue(codes.contains(Badge.FIRST_CONTACT.getCode()));
            assertTrue(codes.contains(Badge.SQUAD_FORMING.getCode()));
            assertTrue(codes.contains(Badge.FULL_PARTY.getCode()));
        }

        @Test
        void testEvaluate_whenBelowTarget_AwardsNothing() {
            metrics.put(BadgeMetric.MATCHES, 2);

            badgeService.evaluate(gamer);

            ArgumentCaptor<List<GamerBadge>> saved = ArgumentCaptor.captor();
            verify(badgeRepository).saveAll(saved.capture());
            assertTrue(saved.getValue().stream()
                    .noneMatch(row -> row.getBadgeCode().equals(Badge.SQUAD_FORMING.getCode())));
        }

        @Test
        @DisplayName("an already-earned badge is not awarded again")
        void testEvaluate_whenAlreadyEarned_DoesNotAwardTwice() {
            earn(Badge.FIRST_CONTACT);
            metrics.put(BadgeMetric.MATCHES, 1);

            badgeService.evaluate(gamer);

            verify(badgeRepository, never()).saveAll(anyList());
            verify(events, never()).publishEvent(any(NotificationRequestedEvent.class));
        }

        @Test
        @DisplayName("earning one notifies the gamer it belongs to")
        void testEvaluate_whenAwarded_PublishesNotification() {
            metrics.put(BadgeMetric.FRIENDS, 1);

            badgeService.evaluate(gamer);

            ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.captor();
            verify(events).publishEvent(event.capture());
            assertEquals("fcm-me", event.getValue().fcmToken());
            assertTrue(event.getValue().body().contains(Badge.FRIENDLY_PERSON.getTitle()));
        }

        @Test
        @DisplayName("metrics come from every source, not just the first")
        void testEvaluate_whenMetricBelongsToAnotherSource_StillCounts() {
            // MESSAGES_SENT is owned by the second fake source. A merge that stopped at
            // the first would silently make every chat mission unreachable.
            metrics.put(BadgeMetric.MESSAGES_SENT, 1);

            badgeService.evaluate(gamer);

            ArgumentCaptor<List<GamerBadge>> saved = ArgumentCaptor.captor();
            verify(badgeRepository).saveAll(saved.capture());
            assertTrue(
                    saved.getValue().stream().anyMatch(row -> row.getBadgeCode().equals(Badge.ICEBREAKER.getCode())));
        }

        @Test
        @DisplayName("opening the screen is what awards them")
        void testGetBadges_whenTargetReached_AwardsBeforeReporting() {
            metrics.put(BadgeMetric.FRIENDS, 1);
            when(badgeRepository.saveAll(anyList())).thenAnswer(i -> {
                rows.addAll(i.getArgument(0));
                return i.getArgument(0);
            });

            BadgesResponseBody body = body(badgeService.getBadges(gamer));

            assertTrue(find(body, Badge.FRIENDLY_PERSON).isEarned());
        }
    }

    @Nested
    class Collecting {

        @Test
        void testCollect_whenValid_CreditsTheReward() {
            GamerBadge row = earn(Badge.FIRST_CONTACT);

            badgeService.collect(gamer, Badge.FIRST_CONTACT.getCode());

            assertEquals(100 + Badge.FIRST_CONTACT.getReward(), gamer.getCoin());
            assertNotNull(row.getCollectedAt());
        }

        @Test
        void testCollect_whenBadgeUnknown_ReturnErrorCode124() {
            BusinessException ex =
                    assertThrows(BusinessException.class, () -> badgeService.collect(gamer, "no-such-badge"));
            assertEquals(124, ex.getTransactionCode().getId());
        }

        @Test
        void testCollect_whenNotEarned_ReturnErrorCode126() {
            String code = Badge.FULL_PARTY.getCode();
            when(badgeRepository.findById(any())).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> badgeService.collect(gamer, code));
            assertEquals(126, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("claiming twice pays once")
        void testCollect_whenAlreadyCollected_ReturnErrorCode125() {
            GamerBadge row = earn(Badge.FIRST_CONTACT);
            row.setCollectedAt(Instant.now());
            String code = Badge.FIRST_CONTACT.getCode();

            BusinessException ex = assertThrows(BusinessException.class, () -> badgeService.collect(gamer, code));
            assertEquals(125, ex.getTransactionCode().getId());
            assertEquals(100, gamer.getCoin());
        }
    }

    @Nested
    class Showcasing {

        @Test
        void testShowcase_whenValid_AssignsSlotsInOrder() {
            earn(Badge.FIRST_CONTACT);
            earn(Badge.ICEBREAKER);

            badgeService.showcase(gamer, List.of(Badge.ICEBREAKER.getCode(), Badge.FIRST_CONTACT.getCode()));

            assertEquals(0, slotOf(Badge.ICEBREAKER));
            assertEquals(1, slotOf(Badge.FIRST_CONTACT));
        }

        @Test
        @DisplayName("re-showcasing clears the old slots before writing the new ones")
        void testShowcase_whenReplacing_ClearsFirst() {
            GamerBadge first = earn(Badge.FIRST_CONTACT);
            first.setShowcaseSlot(0);
            earn(Badge.ICEBREAKER);

            badgeService.showcase(gamer, List.of(Badge.ICEBREAKER.getCode()));

            // Both halves matter: the old badge must lose its slot, and the flush between
            // the two writes is what keeps the unique index from rejecting the second one.
            assertNull(first.getShowcaseSlot());
            assertEquals(0, slotOf(Badge.ICEBREAKER));
            verify(badgeRepository).saveAllAndFlush(anyList());
        }

        @Test
        void testShowcase_whenEmpty_TakesEverythingOff() {
            GamerBadge first = earn(Badge.FIRST_CONTACT);
            first.setShowcaseSlot(0);

            badgeService.showcase(gamer, List.of());

            assertNull(first.getShowcaseSlot());
        }

        @Test
        @DisplayName("a badge that was never earned cannot be shown")
        void testShowcase_whenNotEarned_ReturnErrorCode126() {
            List<String> codes = List.of(Badge.FULL_PARTY.getCode());

            BusinessException ex = assertThrows(BusinessException.class, () -> badgeService.showcase(gamer, codes));
            assertEquals(126, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a bad code leaves the showcase untouched rather than half-cleared")
        void testShowcase_whenOneCodeIsUnknown_ChangesNothing() {
            GamerBadge first = earn(Badge.FIRST_CONTACT);
            first.setShowcaseSlot(0);
            List<String> codes = List.of(Badge.FIRST_CONTACT.getCode(), "no-such-badge");

            assertThrows(BusinessException.class, () -> badgeService.showcase(gamer, codes));
            assertEquals(0, first.getShowcaseSlot());
        }

        @Test
        void testShowcase_whenMoreThanThree_ReturnErrorCode167() {
            List<String> codes = List.of(
                    Badge.FIRST_CONTACT.getCode(),
                    Badge.ICEBREAKER.getCode(),
                    Badge.FRIENDLY_PERSON.getCode(),
                    Badge.SQUAD_FORMING.getCode());

            BusinessException ex = assertThrows(BusinessException.class, () -> badgeService.showcase(gamer, codes));
            assertEquals(167, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("the same badge sent twice takes one slot, not two")
        void testShowcase_whenDuplicated_Collapses() {
            earn(Badge.FIRST_CONTACT);

            badgeService.showcase(gamer, List.of(Badge.FIRST_CONTACT.getCode(), Badge.FIRST_CONTACT.getCode()));

            assertEquals(0, slotOf(Badge.FIRST_CONTACT));
        }

        @Test
        @DisplayName("a profile gets the showcase, not the whole board")
        void testShowcasedFor_whenCalled_ReturnsOnlyWhatIsOnShow() {
            GamerBadge row = new GamerBadge(gamer.getUserId(), Badge.ICEBREAKER.getCode());
            row.setShowcaseSlot(0);
            when(badgeRepository.findAllByUserIdAndShowcaseSlotIsNotNullOrderByShowcaseSlotAsc(gamer.getUserId()))
                    .thenReturn(List.of(row));

            List<ShowcasedBadgeDto> shown = badgeService.showcasedFor(gamer);

            assertEquals(1, shown.size());
            assertEquals(Badge.ICEBREAKER.getTitle(), shown.get(0).getTitle());
            assertEquals("https://cdn/badges/badge-icebreaker.png", shown.get(0).getIcon());
        }

        @Test
        @DisplayName("a row whose mission was retired is skipped, not rendered blank")
        void testShowcasedFor_whenBadgeNoLongerExists_SkipsIt() {
            GamerBadge stale = new GamerBadge(gamer.getUserId(), "a-mission-we-removed");
            stale.setShowcaseSlot(0);
            when(badgeRepository.findAllByUserIdAndShowcaseSlotIsNotNullOrderByShowcaseSlotAsc(gamer.getUserId()))
                    .thenReturn(List.of(stale));

            assertTrue(badgeService.showcasedFor(gamer).isEmpty());
        }

        private Integer slotOf(Badge badge) {
            return rows.stream()
                    .filter(row -> row.getBadgeCode().equals(badge.getCode()))
                    .findFirst()
                    .orElseThrow()
                    .getShowcaseSlot();
        }
    }
}
