package com.gamebuddy.match.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.Platform;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.match.application.mapper.ChatMapper;
import com.gamebuddy.match.application.mapper.ChatMapperImpl;
import com.gamebuddy.match.domain.client.PredictClient;
import com.gamebuddy.match.domain.event.RecommendationServedEvent;
import com.gamebuddy.match.infrastructure.entity.*;
import com.gamebuddy.match.infrastructure.repository.*;
import com.gamebuddy.match.infrastructure.repository.UnlockedAdmirerRepository;
import com.gamebuddy.match.interfaces.dto.GamerDto;
import com.gamebuddy.match.interfaces.request.ColdStartRequest;
import com.gamebuddy.match.interfaces.request.GamerRequest;
import com.gamebuddy.match.interfaces.request.PredictRequest;
import com.gamebuddy.match.interfaces.response.AcceptResponse;
import com.gamebuddy.match.interfaces.response.PredictResponse;
import com.gamebuddy.match.interfaces.response.RecommendationResponse;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.AvatarUrls;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultMatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @InjectMocks
    private DefaultMatchService matchService;

    @Mock
    private PredictClient predictClient;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private GamesRepository gamesRepository;

    @Mock
    private AvatarsRepository avatarsRepository;

    // The one place that decides which picture a gamer shows. Mocked rather than
    // real because it reaches object storage, which these tests have no business
    // standing up.
    @Mock
    private AvatarUrls avatarUrls;

    @Mock
    private CosmeticUrls cosmeticUrls;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private DeclinedMatchRepository declinedMatches;

    /**
     * Admirers bought one at a time. Mocked and empty by default, which is the truthful
     * state for every gamer in these tests: nobody here has paid to reveal anybody.
     */
    @Mock
    private UnlockedAdmirerRepository unlockedAdmirers;

    /**
     * Mocked rather than fixed, so a test can move time forward and watch a decline expire
     * without waiting thirty real days.
     */
    @Mock
    private Clock clock;

    /**
     * A real quota over a fixed clock rather than a mock: the limit is the product
     * behaviour under test in this class, and a mock that silently permits everything
     * would let a broken enforcement path pass.
     */
    @Spy
    private SwipeQuota swipeQuota = new SwipeQuota(Clock.fixed(NOW, ZoneOffset.UTC));

    /** Real, for the same reason as the quota: throttling is behaviour under test here. */
    @Spy
    private RateLimiter decisionRateLimiter = new RateLimiter(30, Duration.ofMinutes(1));

    @Spy
    private ChatMapper chatMapper = new ChatMapperImpl();

    private Gamer gamer;
    private Gamer candidate;

    @BeforeEach
    void setUp() {
        gamer = newGamer("me@example.com", "me");
        candidate = newGamer("candidate@example.com", "candidate");

        when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
        when(gamerRepository.findById(candidate.getUserId())).thenReturn(Optional.of(candidate));
        when(clock.instant()).thenReturn(NOW);
    }

    /**
     * Records that {@code who} has declined these gamers recently enough to still be hidden.
     *
     * <p>The service asks for declines newer than a cutoff, so a test that wants a decline
     * to count answers that question regardless of the cutoff; the tests that care about
     * expiry stub {@link DeclinedMatchRepository#findActiveExclusions} against the horizon
     * themselves.
     */
    private void hasDeclined(Gamer who, String... declinedIds) {
        when(declinedMatches.findActiveExclusions(eq(who.getUserId()), any())).thenReturn(List.of(declinedIds));
    }

    /** Asserts a decline row was written for this pair, dated now. */
    private void assertDeclineRecorded(Gamer who, Gamer declined) {
        ArgumentCaptor<DeclinedMatch> captor = ArgumentCaptor.forClass(DeclinedMatch.class);
        verify(declinedMatches).save(captor.capture());
        DeclinedMatch row = captor.getValue();
        assertEquals(who.getUserId(), row.getUserId());
        assertEquals(declined.getUserId(), row.getDeclinedId());
        assertEquals(NOW, row.getDeclinedAt(), "a decline without a time can never expire");
    }

    private static Gamer newGamer(String email, String username) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setEmail(email);
        g.setGamerUsername(username);
        g.setAge(24);
        g.setCountry("TR");
        g.setGender("M");
        g.setFcmToken("fcm-" + username);
        return g;
    }

    /** Gives a gamer something for the cold-start path to rank from. */
    private static void givenTasteFor(Gamer who, String gameName, String keywordName) {
        Games game = new Games();
        game.setGameId(UUID.randomUUID().toString());
        game.setGameName(gameName);
        who.getLikedgames().add(game);

        Keywords keyword = new Keywords();
        keyword.setId(UUID.randomUUID());
        keyword.setKeywordName(keywordName);
        who.getKeywords().add(keyword);
    }

    /** Registers a mutual match with a new gamer; matches only count when reciprocated. */
    private Gamer reciprocated(String email, String username) {
        Gamer other = newGamer(email, username);
        gamer.getApprovedMatches().add(other);
        other.getApprovedMatches().add(gamer);
        return other;
    }

    private GamerRequest request(Gamer target) {
        GamerRequest r = new GamerRequest();
        r.setUserId(target.getUserId());
        return r;
    }

    @Nested
    class Recommendations {

        @Test
        void testGetRecommendations_whenUserNotFound_ReturnErrorCode103() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> matchService.getRecommendations(gamer));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testGetRecommendations_whenModelUnavailable_ReturnErrorCode123() {
            when(predictClient.predict(any(PredictRequest.class))).thenThrow(new IllegalStateException("down"));

            BusinessException ex = assertThrows(BusinessException.class, () -> matchService.getRecommendations(gamer));
            assertEquals(123, ex.getTransactionCode().getId());
        }

        @Test
        void testGetRecommendations_whenModelReturnsCandidates_ReturnThem() {
            Games game = new Games();
            game.setGameId("g1");
            game.setGameName("Valorant");
            candidate.getLikedgames().add(game);
            Keywords keyword = new Keywords();
            keyword.setId(UUID.randomUUID());
            keyword.setKeywordName("Competitive");
            candidate.getKeywords().add(keyword);

            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of(candidate.getUserId())));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(candidate));
            // Which picture a candidate shows is AvatarUrls' decision now, and it is keyed
            // by gamer id rather than by catalogue id — an uploaded avatar has no row in
            // the catalogue at all. See AvatarUrlsTest for the rules behind it.
            when(avatarUrls.visibleTo(anyCollection())).thenReturn(Map.of(candidate.getUserId(), "img.png"));

            RecommendationResponse response = matchService.getRecommendations(gamer);
            var dto = response.getBody().getData().getRecommendedGamers().get(0);

            assertEquals("100", response.getStatus().getCode());
            assertEquals("candidate", dto.getGamerUsername());
            assertEquals("img.png", dto.getAvatar());
            assertEquals("Valorant", dto.getFavoriteGames().get(0).getGameName());
            assertEquals("Competitive", dto.getSelectedKeywords().get(0));
        }

        @Test
        @DisplayName("a gamer the model has never trained on falls back to cold start")
        void testGetRecommendations_whenModelHasNoVector_UsesColdStart() {
            Games game = new Games();
            game.setGameId("g1");
            game.setGameName("Valorant");
            gamer.getLikedgames().add(game);
            Keywords keyword = new Keywords();
            keyword.setId(UUID.randomUUID());
            keyword.setKeywordName("Competitive");
            gamer.getKeywords().add(keyword);

            // The trained artefact predates this account, so /predict knows nothing about
            // it and answers with an empty list rather than an error.
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of()));
            when(predictClient.predictColdStart(any(ColdStartRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of(candidate.getUserId())));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(candidate));

            RecommendationResponse response = matchService.getRecommendations(gamer);

            assertEquals(
                    1,
                    response.getBody().getData().getRecommendedGamers().size(),
                    "without the fallback every account created since the last retrain sees an empty deck");

            ArgumentCaptor<ColdStartRequest> captor = ArgumentCaptor.forClass(ColdStartRequest.class);
            verify(predictClient).predictColdStart(captor.capture());
            // Names, not ids: the model was trained on the catalogue's names.
            assertEquals(List.of("Valorant"), captor.getValue().games());
            assertEquals(List.of("Competitive"), captor.getValue().keywords());
        }

        @Test
        @DisplayName("a profile edited since the last retrain is ranked live, not from the stale vector")
        void testGetRecommendations_whenProfileChangedSinceTraining_UsesColdStart() {
            givenTasteFor(gamer, "Valorant", "Competitive");
            // Set by RecommenderStalenessListener when the gamer changed their games. The
            // artefact still holds whatever they liked before that.
            gamer.setRecommenderProfileChangedAt(NOW.minusSeconds(60));

            when(predictClient.predictColdStart(any(ColdStartRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of(candidate.getUserId())));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(candidate));

            RecommendationResponse response = matchService.getRecommendations(gamer);

            assertEquals(1, response.getBody().getData().getRecommendedGamers().size());
            ArgumentCaptor<ColdStartRequest> captor = ArgumentCaptor.forClass(ColdStartRequest.class);
            verify(predictClient).predictColdStart(captor.capture());
            assertEquals(List.of("Valorant"), captor.getValue().games());
            verify(predictClient, never()).predict(any(PredictRequest.class));
        }

        @Test
        @DisplayName("an unedited profile still goes to the trained model, which knows more than the profile does")
        void testGetRecommendations_whenProfileUnchanged_UsesTheTrainedVector() {
            givenTasteFor(gamer, "Valorant", "Competitive");
            assertNull(gamer.getRecommenderProfileChangedAt(), "nothing has been edited");

            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of(candidate.getUserId())));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(candidate));

            matchService.getRecommendations(gamer);

            verify(predictClient, never()).predictColdStart(any());
        }

        @Test
        @DisplayName("a stale gamer who has emptied their profile falls back rather than showing an empty deck")
        void testGetRecommendations_whenStaleButProfileEmpty_FallsBackToTheTrainedVector() {
            gamer.setRecommenderProfileChangedAt(NOW.minusSeconds(60));

            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of(candidate.getUserId())));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(candidate));

            RecommendationResponse response = matchService.getRecommendations(gamer);

            assertEquals(
                    1,
                    response.getBody().getData().getRecommendedGamers().size(),
                    "a stale ranking beats no ranking at all");
            verify(predictClient, never()).predictColdStart(any());
        }

        @Test
        @DisplayName("a profile with nothing on it does not bother the cold-start endpoint")
        void testGetRecommendations_whenProfileEmpty_SkipsColdStart() {
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of()));

            matchService.getRecommendations(gamer);

            verify(predictClient, never()).predictColdStart(any());
        }

        @Test
        @DisplayName("gamers already decided on are sent to the model as exclusions, not filtered from its reply")
        void testGetRecommendations_whenAlreadyDecided_SendsThemAsExclusions() {
            gamer.getApprovedMatches().add(candidate);
            hasDeclined(gamer, "declined-1");

            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of()));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of());

            matchService.getRecommendations(gamer);

            ArgumentCaptor<PredictRequest> captor = ArgumentCaptor.forClass(PredictRequest.class);
            verify(predictClient).predict(captor.capture());
            PredictRequest sent = captor.getValue();

            // Filtering the model's reply instead of excluding up front is what made the
            // feed run dry: the ranking is deterministic, so the same page came back
            // forever once its occupants had been swiped.
            assertTrue(sent.exclude().contains(candidate.getUserId()));
            assertTrue(sent.exclude().contains("declined-1"));
            assertTrue(sent.exclude().contains(gamer.getUserId()), "never recommend yourself");
            assertTrue(sent.limit() > 50, "over-fetch, because age band and blocks still filter locally");
        }

        @Test
        @DisplayName("the model's ranking survives findAllById, which returns rows in database order")
        void testGetRecommendations_preservesModelRanking() {
            Gamer second = new Gamer();
            second.setUserId("b");
            second.setAge(gamer.getAge());
            second.setGamerUsername("second");
            Gamer third = new Gamer();
            third.setUserId("c");
            third.setAge(gamer.getAge());
            third.setGamerUsername("third");

            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of(candidate.getUserId(), "b", "c")));
            // Deliberately the wrong order: findAllById issues WHERE id IN (...) and makes
            // no ordering guarantee, so the ranking was previously discarded outright.
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(third, candidate, second));

            List<GamerDto> recommended =
                    matchService.getRecommendations(gamer).getBody().getData().getRecommendedGamers();

            assertEquals(
                    List.of(candidate.getUserId(), "b", "c"),
                    recommended.stream().map(GamerDto::getUserId).toList());
        }

        @Test
        @DisplayName("a slice of the page is randomly explored, not similarity-ranked")
        void testGetRecommendations_mixesInExploredCandidates() {
            List<Gamer> ranked = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                Gamer g = new Gamer();
                g.setUserId("ranked-" + i);
                g.setAge(gamer.getAge());
                ranked.add(g);
                ids.add(g.getUserId());
            }
            Gamer explored = new Gamer();
            explored.setUserId("explored-1");
            explored.setAge(gamer.getAge());

            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), ids));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(ranked);
            when(gamerRepository.findRandomPairable(
                            anyBoolean(), any(String[].class), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(explored));

            List<String> page =
                    matchService.getRecommendations(gamer).getBody().getData().getRecommendedGamers().stream()
                            .map(GamerDto::getUserId)
                            .toList();

            assertTrue(page.contains("explored-1"), "an unranked gamer must still be reachable");
            // Replaces the tail rather than extending the page.
            assertEquals(20, page.size());
            assertFalse(page.contains("ranked-19"));
        }

        @Test
        @DisplayName("exploration samples the gamer's own age band and skips anyone already decided on")
        void testGetRecommendations_exploresWithinTheAgeBandOnly() {
            gamer.setAge(15);
            hasDeclined(gamer, candidate.getUserId());

            List<String> ids = new ArrayList<>();
            List<Gamer> ranked = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Gamer g = new Gamer();
                g.setUserId("r" + i);
                g.setAge(15);
                ranked.add(g);
                ids.add(g.getUserId());
            }
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), ids));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(ranked);
            when(gamerRepository.findRandomPairable(
                            anyBoolean(), any(String[].class), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of());

            matchService.getRecommendations(gamer);

            ArgumentCaptor<Boolean> minor = ArgumentCaptor.forClass(Boolean.class);
            ArgumentCaptor<String[]> excluded = ArgumentCaptor.forClass(String[].class);
            verify(gamerRepository)
                    .findRandomPairable(minor.capture(), excluded.capture(), any(), any(), any(), any(), anyInt());

            assertTrue(minor.getValue(), "a minor must only ever be shown other minors");
            List<String> skip = List.of(excluded.getValue());
            assertTrue(skip.contains(candidate.getUserId()), "already declined");
            assertTrue(skip.contains(gamer.getUserId()), "never yourself");
            assertTrue(skip.contains("r0"), "already on the page");
        }

        @Test
        @DisplayName("a blocked gamer is never explored into the page")
        void testGetRecommendations_exploredCandidatesRespectBlocks() {
            Gamer blocked = new Gamer();
            blocked.setUserId("blocked-1");
            blocked.setAge(gamer.getAge());
            gamer.getBlockedFriends().add(blocked);

            List<String> ids = new ArrayList<>();
            List<Gamer> ranked = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Gamer g = new Gamer();
                g.setUserId("r" + i);
                g.setAge(gamer.getAge());
                ranked.add(g);
                ids.add(g.getUserId());
            }
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), ids));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(ranked);
            // The SQL cannot see the block join table, so an unfiltered row comes back.
            when(gamerRepository.findRandomPairable(
                            anyBoolean(), any(String[].class), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(blocked));

            List<String> page =
                    matchService.getRecommendations(gamer).getBody().getData().getRecommendedGamers().stream()
                            .map(GamerDto::getUserId)
                            .toList();

            assertFalse(page.contains("blocked-1"));
        }

        @Test
        @DisplayName("what was shown is recorded, with position and source, for the next retrain")
        void testGetRecommendations_recordsImpressions() {
            List<String> ids = new ArrayList<>();
            List<Gamer> ranked = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Gamer g = new Gamer();
                g.setUserId("r" + i);
                g.setAge(gamer.getAge());
                ranked.add(g);
                ids.add(g.getUserId());
            }
            Gamer explored = new Gamer();
            explored.setUserId("explored-1");
            explored.setAge(gamer.getAge());

            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), ids));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(ranked);
            when(gamerRepository.findRandomPairable(
                            anyBoolean(), any(String[].class), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(explored));

            matchService.getRecommendations(gamer);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(events, atLeastOnce()).publishEvent(captor.capture());
            RecommendationServedEvent event = captor.getAllValues().stream()
                    .filter(RecommendationServedEvent.class::isInstance)
                    .map(RecommendationServedEvent.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no impression event was published"));

            assertEquals(gamer.getUserId(), event.userId());
            assertEquals(10, event.candidates().size());

            // Position is most of the signal in click data, so it has to be recorded.
            assertEquals(0, event.candidates().get(0).position());
            assertEquals(9, event.candidates().get(9).position());

            // The explored slot must be distinguishable: fitting the desirability prior on
            // model-chosen impressions alone would just measure the model's own opinions.
            var last = event.candidates().get(9);
            assertEquals("explored-1", last.candidateId());
            assertEquals(ImpressionSource.EXPLORATION, last.source());
            assertEquals(ImpressionSource.MODEL, event.candidates().get(0).source());
        }

        @Test
        @DisplayName("an empty page publishes no impression event")
        void testGetRecommendations_whenPageEmpty_RecordsNothing() {
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of()));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of());

            matchService.getRecommendations(gamer);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(events, atLeast(0)).publishEvent(captor.capture());
            assertTrue(captor.getAllValues().stream().noneMatch(RecommendationServedEvent.class::isInstance));
        }

        @Test
        @DisplayName("an id the model knows but the database does not is skipped, not a 404 for the whole screen")
        void testGetRecommendations_whenCandidateMissing_SkipsIt() {
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), new ArrayList<>(List.of("ghost"))));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of());

            RecommendationResponse response = matchService.getRecommendations(gamer);

            assertEquals("100", response.getStatus().getCode());
            assertTrue(response.getBody().getData().getRecommendedGamers().isEmpty());
        }

        @Test
        void testGetSelectedGameRecommendations_whenGameNotFound_ReturnErrorCode151() {
            when(gamesRepository.findById("nope")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(
                    BusinessException.class, () -> matchService.getSelectedGameRecommendations(gamer, "nope"));
            assertEquals(151, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("asking for game recommendations does not un-like the game for anyone")
        void testGetSelectedGameRecommendations_whenCalled_DoesNotMutateTheGamesCollection() {
            Games game = new Games();
            game.setGameId("g1");
            game.setGameName("Valorant");
            game.getGamers().add(gamer);
            game.getGamers().add(candidate);
            when(gamesRepository.findById("g1")).thenReturn(Optional.of(game));

            RecommendationResponse response = matchService.getSelectedGameRecommendations(gamer, "g1");

            assertEquals(1, response.getBody().getData().getRecommendedGamers().size());
            // The old code called remove() on the managed set, which deletes the row
            // from gamer_games_join.
            assertEquals(2, game.getGamers().size(), "the entity collection must not be modified");
        }
    }

    /**
     * The Gold filters, and how they reach the model.
     *
     * <p>Every test here exists because of one production defect: the backend used to send
     * the model everybody the filter ruled <em>out</em>, which in a 20,001-gamer database
     * was past the model's ten-thousand cap. The model refused the request, the backend
     * reported it as {@code RECOMMENDER_SERVICE_ERROR}, and three of the four filters
     * answered 503 to the only people who had paid to use them.
     */
    @Nested
    class AdvancedFilters {

        private Gamer subscriber;

        @BeforeEach
        void goldSubscriber() {
            subscriber = gamer;
            subscriber.setSubscriptionTier(SubscriptionTier.GOLD);
            subscriber.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));
        }

        /** Stubs the model to echo back whatever it is allowed to rank. */
        private void modelRanks(List<String> ids, List<Gamer> rows) {
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(subscriber.getUserId(), ids));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(rows);
        }

        private PredictRequest captureRequest() {
            ArgumentCaptor<PredictRequest> captor = ArgumentCaptor.forClass(PredictRequest.class);
            verify(predictClient).predict(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("a narrowed feed sends the eligible set, not its complement")
        void testGetRecommendations_sendsAnIncludeList() {
            Gamer eligible = newGamer("fi@example.com", "fi");
            eligible.setCountry("FI");
            when(gamerRepository.findIdsMatchingFilters(isNull(), eq("FI"), isNull(), isNull()))
                    .thenReturn(List.of(eligible.getUserId()));
            modelRanks(List.of(eligible.getUserId()), List.of(eligible));

            matchService.getRecommendations(subscriber, new FeedFilters(null, "FI", null, null));

            PredictRequest sent = captureRequest();
            assertEquals(List.of(eligible.getUserId()), sent.include());
            assertFalse(
                    sent.exclude().contains(eligible.getUserId()),
                    "the filter must not reach the model as an exclusion — that is the shape that broke");
        }

        @Test
        @DisplayName("an unfiltered feed sends no include list at all")
        void testGetRecommendations_unfilteredSendsNoInclude() {
            modelRanks(List.of(candidate.getUserId()), List.of(candidate));

            matchService.getRecommendations(subscriber);

            assertNull(captureRequest().include(), "null is 'rank over everybody'");
            verify(gamerRepository, never()).findIdsMatchingFilters(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a filter that matches nobody produces an empty deck, not an unfiltered one")
        void testGetRecommendations_emptyEligibleSetIsNotUnfiltered() {
            when(gamerRepository.findIdsMatchingFilters(any(), any(), any(), any()))
                    .thenReturn(List.of());

            RecommendationResponse response =
                    matchService.getRecommendations(subscriber, new FeedFilters(null, "AQ", null, null));

            // Empty and null are opposite answers. Treating empty as "not filtering" would
            // rank over the whole population and show somebody who asked for people in
            // Antarctica a page of people who are not — the filter apparently ignored.
            assertTrue(response.getBody().getData().getRecommendedGamers().isEmpty());
            // And nobody is asked, because the answer is already known. An empty result from
            // the model normally means "never met this gamer" and triggers the cold-start
            // fallback; here it would mean something else with the same shape, so both round
            // trips would be spent learning nothing.
            verify(predictClient, never()).predict(any());
            verify(predictClient, never()).predictColdStart(any());
        }

        @Test
        @DisplayName("an eligible set past the model's cap falls back to ranking unfiltered")
        void testGetRecommendations_overTheCapDegradesRatherThanFailing() {
            List<String> everybody = new ArrayList<>();
            for (int i = 0; i < 50_001; i++) {
                everybody.add("g" + i);
            }
            when(gamerRepository.findIdsMatchingFilters(any(), any(), any(), any()))
                    .thenReturn(everybody);

            Gamer onPlatform = newGamer("pc@example.com", "pc");
            onPlatform.getPlatforms().add(Platform.PC);
            Gamer elsewhere = newGamer("ps@example.com", "ps");
            elsewhere.getPlatforms().add(Platform.PLAYSTATION);
            modelRanks(List.of(onPlatform.getUserId(), elsewhere.getUserId()), List.of(onPlatform, elsewhere));

            RecommendationResponse response =
                    matchService.getRecommendations(subscriber, new FeedFilters(null, null, null, Platform.PC));

            // No 503, and no include list — a set this size means the filter excluded almost
            // nobody, so the ranking is very nearly the filtered one already.
            assertNull(captureRequest().include());
            // And the answer is still narrowed, by the in-memory check.
            List<String> page = response.getBody().getData().getRecommendedGamers().stream()
                    .map(GamerDto::getUserId)
                    .toList();
            assertEquals(List.of(onPlatform.getUserId()), page);
        }

        @Test
        @DisplayName("exploration draws from the filtered pool rather than filtering afterwards")
        void testGetRecommendations_explorationIsFilteredInTheQuery() {
            List<String> ids = new ArrayList<>();
            List<Gamer> ranked = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Gamer g = newGamer("r" + i + "@example.com", "r" + i);
                g.setCountry("FI");
                ranked.add(g);
                ids.add(g.getUserId());
            }
            when(gamerRepository.findIdsMatchingFilters(any(), any(), any(), any()))
                    .thenReturn(ids);
            modelRanks(ids, ranked);
            when(gamerRepository.findRandomPairable(
                            anyBoolean(), any(String[].class), any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of());

            matchService.getRecommendations(subscriber, new FeedFilters(null, "FI", null, null));

            // Without this the exploration slots would be drawn from the whole population and
            // then discarded by `filtered`, so a narrow filter would silently lose them —
            // the same failure one layer down.
            ArgumentCaptor<String> country = ArgumentCaptor.forClass(String.class);
            verify(gamerRepository)
                    .findRandomPairable(
                            anyBoolean(), any(String[].class), any(), country.capture(), any(), any(), anyInt());
            assertEquals("FI", country.getValue());
        }

        @Test
        @DisplayName("the cold-start path carries the filter too")
        void testGetRecommendations_coldStartSendsTheIncludeList() {
            givenTasteFor(subscriber, "VALORANT", "competitive");
            subscriber.setRecommenderProfileChangedAt(NOW);

            Gamer eligible = newGamer("fi@example.com", "fi");
            when(gamerRepository.findIdsMatchingFilters(any(), any(), any(), any()))
                    .thenReturn(List.of(eligible.getUserId()));
            when(predictClient.predictColdStart(any(ColdStartRequest.class)))
                    .thenReturn(new PredictResponse(subscriber.getUserId(), List.of(eligible.getUserId())));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(eligible));

            matchService.getRecommendations(subscriber, new FeedFilters(null, "FI", null, null));

            // A subscriber who signed up since the last retrain is served here, and their
            // filters have to work on day one rather than after the next nightly run.
            ArgumentCaptor<ColdStartRequest> captor = ArgumentCaptor.forClass(ColdStartRequest.class);
            verify(predictClient).predictColdStart(captor.capture());
            assertEquals(List.of(eligible.getUserId()), captor.getValue().include());
        }

        @Test
        @DisplayName("a non-subscriber is refused before any of this work is done")
        void testGetRecommendations_filtersStillNeedGold() {
            subscriber.setSubscriptionTier(SubscriptionTier.BASIC);
            subscriber.setSubscriptionExpiresAt(null);

            assertThrows(
                    BusinessException.class,
                    () -> matchService.getRecommendations(subscriber, new FeedFilters(null, "FI", null, null)));
            verify(gamerRepository, never()).findIdsMatchingFilters(any(), any(), any(), any());
        }
    }

    @Nested
    class AcceptAndDecline {

        @Test
        void testAcceptGamer_whenTargetNotFound_ReturnErrorCode103() {
            when(gamerRepository.findById(candidate.getUserId())).thenReturn(Optional.empty());
            GamerRequest req = request(candidate);

            BusinessException ex = assertThrows(BusinessException.class, () -> matchService.acceptGamer(gamer, req));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testAcceptGamer_whenAcceptingSelf_ReturnErrorCode148() {
            GamerRequest req = request(gamer);

            BusinessException ex = assertThrows(BusinessException.class, () -> matchService.acceptGamer(gamer, req));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        void testAcceptGamer_whenValid_ReturnSuccess() {
            AcceptResponse response = matchService.acceptGamer(gamer, request(candidate));

            assertEquals("100", response.getStatus().getCode());
            assertTrue(gamer.getApprovedMatches().contains(candidate));
        }

        @Test
        @DisplayName("a one-sided accept reports matched=false")
        void testAcceptGamer_whenOneSided_ReportsNotMatched() {
            AcceptResponse response = matchService.acceptGamer(gamer, request(candidate));

            assertFalse(response.getBody().getData().isMatched());
        }

        @Test
        @DisplayName("a reciprocated accept reports matched=true")
        void testAcceptGamer_whenReciprocated_ReportsMatched() {
            candidate.getApprovedMatches().add(gamer);

            AcceptResponse response = matchService.acceptGamer(gamer, request(candidate));

            // The flag, not the message: the client must not have to string-match copy
            // to know whether to celebrate.
            assertTrue(response.getBody().getData().isMatched());
        }

        @Test
        @DisplayName("accepting someone previously declined clears the decline")
        void testAcceptGamer_whenPreviouslyDeclined_ClearsTheDecline() {
            hasDeclined(gamer, candidate.getUserId());

            matchService.acceptGamer(gamer, request(candidate));

            assertTrue(gamer.getApprovedMatches().contains(candidate));
            // A gamer can only be in one state at a time. Leaving the decline behind would
            // mean the two sets disagreed about what was decided.
            verify(declinedMatches).clear(gamer.getUserId(), candidate.getUserId());
        }

        @Test
        void testDeclineGamer_whenValid_ReturnSuccess() {
            DefaultMessageResponse response = matchService.declineGamer(gamer, request(candidate));

            assertEquals("100", response.getStatus().getCode());
            assertDeclineRecorded(gamer, candidate);
        }

        @Test
        void testDeclineGamer_whenPreviouslyAccepted_ClearsTheAcceptance() {
            gamer.getApprovedMatches().add(candidate);

            matchService.declineGamer(gamer, request(candidate));

            assertDeclineRecorded(gamer, candidate);
            assertFalse(gamer.getApprovedMatches().contains(candidate));
        }

        @Test
        void testDeclineGamer_whenDecliningSelf_ReturnErrorCode148() {
            GamerRequest req = request(gamer);

            BusinessException ex = assertThrows(BusinessException.class, () -> matchService.declineGamer(gamer, req));
            assertEquals(148, ex.getTransactionCode().getId());
        }
    }

    @Nested
    @DisplayName("who may be paired with whom")
    class Pairing {

        private void modelReturns(Gamer candidateGamer) {
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(
                            gamer.getUserId(), new ArrayList<>(List.of(candidateGamer.getUserId()))));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of(candidateGamer));
        }

        private List<?> recommended() {
            return matchService.getRecommendations(gamer).getBody().getData().getRecommendedGamers();
        }

        @Test
        @DisplayName("a minor is never recommended to an adult")
        void testGetRecommendations_whenCandidateIsAMinor_ExcludesThem() {
            gamer.setAge(30);
            candidate.setAge(16);
            modelReturns(candidate);

            assertTrue(recommended().isEmpty());
        }

        @Test
        @DisplayName("an adult is never recommended to a minor")
        void testGetRecommendations_whenCandidateIsAnAdult_ExcludesThemFromAMinor() {
            gamer.setAge(15);
            candidate.setAge(28);
            modelReturns(candidate);

            assertTrue(recommended().isEmpty());
        }

        @Test
        void testGetRecommendations_whenSameBand_IncludesThem() {
            gamer.setAge(15);
            candidate.setAge(17);
            modelReturns(candidate);

            assertEquals(1, recommended().size());
        }

        @Test
        @DisplayName("a gamer this one has blocked is not recommended")
        void testGetRecommendations_whenBlocked_ExcludesThem() {
            gamer.getBlockedFriends().add(candidate);
            modelReturns(candidate);

            assertTrue(recommended().isEmpty());
        }

        @Test
        @DisplayName("a gamer who blocked this one is not recommended either")
        void testGetRecommendations_whenBlockedByCandidate_ExcludesThem() {
            // The block is recorded on the blocker; it has to be honoured from both ends.
            candidate.getBlockedFriends().add(gamer);
            modelReturns(candidate);

            assertTrue(recommended().isEmpty());
        }

        @Test
        @DisplayName("a banned account is not recommended")
        void testGetRecommendations_whenCandidateIsBanned_ExcludesThem() {
            candidate.setIsBlocked(true);
            modelReturns(candidate);

            assertTrue(recommended().isEmpty());
        }

        @Test
        void testAcceptGamer_whenDifferentAgeBand_ReturnErrorCode154() {
            gamer.setAge(16);
            candidate.setAge(30);
            GamerRequest req = request(candidate);

            BusinessException ex = assertThrows(BusinessException.class, () -> matchService.acceptGamer(gamer, req));
            assertEquals(154, ex.getTransactionCode().getId());
        }

        @Test
        void testAcceptGamer_whenBlocked_ReturnErrorCode113() {
            gamer.getBlockedFriends().add(candidate);
            GamerRequest req = request(candidate);

            BusinessException ex = assertThrows(BusinessException.class, () -> matchService.acceptGamer(gamer, req));
            assertEquals(113, ex.getTransactionCode().getId());
        }

        @Test
        void testAcceptGamer_whenTargetIsBanned_ReturnErrorCode113() {
            candidate.setIsBlocked(true);
            GamerRequest req = request(candidate);

            BusinessException ex = assertThrows(BusinessException.class, () -> matchService.acceptGamer(gamer, req));
            assertEquals(113, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("declining is always allowed, whatever the band or block state")
        void testDeclineGamer_whenDifferentAgeBand_StillSucceeds() {
            gamer.setAge(16);
            candidate.setAge(30);

            assertEquals(
                    "100",
                    matchService
                            .declineGamer(gamer, request(candidate))
                            .getStatus()
                            .getCode());
        }
    }

    @Nested
    @DisplayName("a match is mutual, and both sides are told")
    class MutualMatch {

        @Test
        void testAcceptGamer_whenOneSided_DoesNotAnnounceAMatch() {
            matchService.acceptGamer(gamer, request(candidate));

            verify(events, never()).publishEvent(any(NotificationRequestedEvent.class));
        }

        @Test
        @DisplayName("when the other side has already accepted, both gamers are notified")
        void testAcceptGamer_whenReciprocated_NotifiesBoth() {
            candidate.getApprovedMatches().add(gamer);

            matchService.acceptGamer(gamer, request(candidate));

            ArgumentCaptor<NotificationRequestedEvent> captor =
                    ArgumentCaptor.forClass(NotificationRequestedEvent.class);
            verify(events, times(2)).publishEvent(captor.capture());
            Set<String> notified = captor.getAllValues().stream()
                    .map(NotificationRequestedEvent::fcmToken)
                    .collect(Collectors.toSet());
            assertEquals(Set.of("fcm-me", "fcm-candidate"), notified);
        }

        @Test
        @DisplayName("getMatches returns only the reciprocated ones")
        void testGetMatches_whenCalled_ReturnsOnlyMutual() {
            gamer.getApprovedMatches().add(newGamer("one@example.com", "one"));
            gamer.getApprovedMatches().add(candidate);
            candidate.getApprovedMatches().add(gamer);

            RecommendationResponse response = matchService.getMatches(gamer);

            assertEquals(1, response.getBody().getData().getRecommendedGamers().size());
            assertEquals(
                    "candidate",
                    response.getBody().getData().getRecommendedGamers().get(0).getGamerUsername());
        }

        @Test
        @DisplayName("a blocked gamer drops out of the match list without being unmatched")
        void testGetMatches_whenBlocked_ExcludesThem() {
            gamer.getApprovedMatches().add(candidate);
            candidate.getApprovedMatches().add(gamer);
            gamer.getBlockedFriends().add(candidate);

            assertTrue(matchService
                    .getMatches(gamer)
                    .getBody()
                    .getData()
                    .getRecommendedGamers()
                    .isEmpty());
        }
    }

    @Nested
    class Monetization {

        /** Likes gone, swipes remaining: the gamer can still browse. */
        private void spendTheAccepts() {
            gamer.setAcceptsUsed(SubscriptionTier.BASIC.dailyAccepts());
            gamer.setSwipesUsed(SubscriptionTier.BASIC.dailyAccepts());
            gamer.setQuotaResetAt(NOW.plus(Duration.ofHours(12)));
        }

        /** Whole budget gone: nothing at all is possible today. */
        private void spendTheWholeBudget() {
            gamer.setSwipesUsed(SubscriptionTier.BASIC.dailySwipes());
            gamer.setAcceptsUsed(0);
            gamer.setQuotaResetAt(NOW.plus(Duration.ofHours(12)));
        }

        @Test
        @DisplayName("the free tier is refused once the daily likes are spent")
        void testAcceptGamer_whenAcceptsSpent_Refuses() {
            spendTheAccepts();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> matchService.acceptGamer(gamer, request(candidate)));
            assertEquals(158, ex.getTransactionCode().getId());
            assertFalse(gamer.getApprovedMatches().contains(candidate), "the decision was not recorded");
        }

        @Test
        @DisplayName("with the likes spent but swipes left, browsing carries on")
        void testDeclineGamer_stillWorksWithAcceptsSpent() {
            spendTheAccepts();

            assertDoesNotThrow(() -> matchService.declineGamer(gamer, request(candidate)));
            assertDeclineRecorded(gamer, candidate);
        }

        @Test
        @DisplayName("accepts and declines come out of the same budget")
        void testDecisions_shareOneBudget() {
            for (int i = 0; i < 20; i++) {
                Gamer other = newGamer("other" + i + "@example.com", "other" + i);
                when(gamerRepository.findById(other.getUserId())).thenReturn(Optional.of(other));
                matchService.declineGamer(gamer, request(other));
            }

            assertEquals(20, gamer.getSwipesUsed());
            assertEquals(0, gamer.getAcceptsUsed(), "twenty declines cost no likes");

            matchService.acceptGamer(gamer, request(candidate));
            assertEquals(21, gamer.getSwipesUsed(), "an accept costs a swipe as well");
            assertEquals(1, gamer.getAcceptsUsed());
        }

        @Test
        @DisplayName("an exhausted budget stops declining too, and reports the swipe limit")
        void testDeclineGamer_whenWholeBudgetSpent_Refuses() {
            spendTheWholeBudget();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> matchService.declineGamer(gamer, request(candidate)));
            assertEquals(163, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("an exhausted budget reports the swipe limit even for an accept, not the like limit")
        void testAcceptGamer_whenWholeBudgetSpent_ReportsSwipeLimit() {
            spendTheWholeBudget();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> matchService.acceptGamer(gamer, request(candidate)));
            assertEquals(163, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("decisions arriving faster than a person can make them are throttled")
        void testDecisions_areRateLimited() {
            // The limiter is separate from the daily allowance and measured in a minute:
            // a daily cap does not stop a script, it just runs to the cap instantly.
            for (int i = 0; i < 30; i++) {
                Gamer other = newGamer("flood" + i + "@example.com", "flood" + i);
                when(gamerRepository.findById(other.getUserId())).thenReturn(Optional.of(other));
                matchService.declineGamer(gamer, request(other));
            }

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> matchService.declineGamer(gamer, request(candidate)));
            assertEquals(146, ex.getTransactionCode().getId(), "RATE_LIMITED");
        }

        @Test
        @DisplayName("a gold account is throttled too: the rate limit is about abuse, not tier")
        void testDecisions_rateLimitAppliesToGoldAsWell() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            for (int i = 0; i < 30; i++) {
                Gamer other = newGamer("flood" + i + "@example.com", "flood" + i);
                when(gamerRepository.findById(other.getUserId())).thenReturn(Optional.of(other));
                matchService.declineGamer(gamer, request(other));
            }

            assertThrows(BusinessException.class, () -> matchService.declineGamer(gamer, request(candidate)));
        }

        @Test
        void testAcceptGamer_whenGold_IsNotRationed() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));
            spendTheWholeBudget();

            assertDoesNotThrow(() -> matchService.acceptGamer(gamer, request(candidate)));
            assertTrue(gamer.getApprovedMatches().contains(candidate));
        }

        @Test
        @DisplayName("the free tier gets the count of admirers but not their identities")
        void testGetWhoLikedYou_whenBasic_IsLocked() {
            when(gamerRepository.findPendingAdmirers(gamer.getUserId())).thenReturn(List.of(candidate));

            var body = matchService.getWhoLikedYou(gamer).getBody().getData();

            assertEquals(1, body.getCount(), "the count is the hook and stays free");
            assertTrue(body.isLocked());
            assertTrue(body.getLikedYou().isEmpty(), "identities are the paid part");
        }

        @Test
        void testGetWhoLikedYou_whenGold_ReturnsIdentities() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));
            when(gamerRepository.findPendingAdmirers(gamer.getUserId())).thenReturn(List.of(candidate));

            var body = matchService.getWhoLikedYou(gamer).getBody().getData();

            assertFalse(body.isLocked());
            assertEquals(1, body.getLikedYou().size());
            assertEquals("candidate", body.getLikedYou().get(0).getGamerUsername());
        }

        @Test
        @DisplayName("an expired subscription locks the feature again, with nothing writing BASIC back")
        void testGetWhoLikedYou_whenSubscriptionExpired_IsLocked() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.minus(Duration.ofSeconds(1)));
            when(gamerRepository.findPendingAdmirers(gamer.getUserId())).thenReturn(List.of(candidate));

            assertTrue(matchService.getWhoLikedYou(gamer).getBody().getData().isLocked());
        }

        @Test
        @DisplayName("someone already answered is not a pending like, so it is not sold back to the gamer")
        void testGetWhoLikedYou_excludesAlreadyDecided() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));
            gamer.getApprovedMatches().add(candidate);
            when(gamerRepository.findPendingAdmirers(gamer.getUserId())).thenReturn(List.of(candidate));

            assertEquals(
                    0, matchService.getWhoLikedYou(gamer).getBody().getData().getCount());
        }

        @Test
        @DisplayName("an admirer in the other age band is never surfaced, paid tier or not")
        void testGetWhoLikedYou_respectsTheAgeBand() {
            gamer.setAge(15);
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));
            candidate.setAge(30);
            when(gamerRepository.findPendingAdmirers(gamer.getUserId())).thenReturn(List.of(candidate));

            assertEquals(
                    0, matchService.getWhoLikedYou(gamer).getBody().getData().getCount());
        }

        @Test
        void testGetSwipeAllowance_reportsBothCountsAndReset() {
            gamer.setSwipesUsed(12);
            gamer.setAcceptsUsed(3);
            gamer.setQuotaResetAt(NOW.plus(Duration.ofHours(5)));

            var body = matchService.getSwipeAllowance(gamer).getBody().getData();

            assertEquals("BASIC", body.getTier());
            assertEquals(SubscriptionTier.BASIC.dailySwipes() - 12, body.getRemainingSwipes());
            assertEquals(SubscriptionTier.BASIC.dailyAccepts() - 3, body.getRemainingAccepts());
            assertFalse(body.isUnlimited());
            assertEquals(NOW.plus(Duration.ofHours(5)), body.getResetsAt());
        }

        @Test
        void testGetSwipeAllowance_whenGold_IsUnlimited() {
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(30)));

            var body = matchService.getSwipeAllowance(gamer).getBody().getData();

            assertEquals("GOLD", body.getTier());
            assertTrue(body.isUnlimited());
            assertNull(body.getResetsAt());
        }
    }

    /**
     * Declines expire.
     *
     * <p>A permanent pass is a slow leak: the ranking is a deterministic function of
     * profiles, so everyone ever declined is gone from the pool for good and an active
     * swiper eventually reaches an empty feed with no way back. Thirty days later the
     * profile that was passed over is usually not the profile that exists now.
     */
    @Nested
    class DeclineRecycling {

        @Test
        @DisplayName("the exclusion query only asks for declines inside the window")
        void asksOnlyForRecentDeclines() {
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of()));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of());

            matchService.getRecommendations(gamer);

            ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
            verify(declinedMatches, atLeastOnce()).findActiveExclusions(eq(gamer.getUserId()), since.capture());
            // Instant.EPOCH or null here would be the old behaviour wearing a timestamp:
            // the column would exist and nothing would ever age out of it.
            assertEquals(NOW.minus(Duration.ofDays(30)), since.getValue());
        }

        @Test
        @DisplayName("a decline past the window stops hiding the gamer")
        void expiredDeclineIsNoLongerExcluded() {
            // Nothing inside the window: the old decline exists in the table but falls the
            // wrong side of the cutoff, so the query does not return it.
            when(declinedMatches.findActiveExclusions(eq(gamer.getUserId()), any()))
                    .thenReturn(List.of());
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of()));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of());

            matchService.getRecommendations(gamer);

            ArgumentCaptor<PredictRequest> captor = ArgumentCaptor.forClass(PredictRequest.class);
            verify(predictClient).predict(captor.capture());
            assertFalse(
                    captor.getValue().exclude().contains(candidate.getUserId()),
                    "an expired decline must let the gamer back into the pool");
        }

        @Test
        @DisplayName("a decline inside the window still hides the gamer")
        void freshDeclineIsStillExcluded() {
            hasDeclined(gamer, candidate.getUserId());
            when(predictClient.predict(any(PredictRequest.class)))
                    .thenReturn(new PredictResponse(gamer.getUserId(), List.of()));
            when(gamerRepository.findAllById(anyIterable())).thenReturn(List.of());

            matchService.getRecommendations(gamer);

            ArgumentCaptor<PredictRequest> captor = ArgumentCaptor.forClass(PredictRequest.class);
            verify(predictClient).predict(captor.capture());
            assertTrue(
                    captor.getValue().exclude().contains(candidate.getUserId()),
                    "recycling after thirty days must not mean recycling immediately");
        }

        @Test
        @DisplayName("declining again restarts the clock rather than keeping the first date")
        void redecliningRefreshesTheTimestamp() {
            when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(45)));

            matchService.declineGamer(gamer, request(candidate));

            ArgumentCaptor<DeclinedMatch> captor = ArgumentCaptor.forClass(DeclinedMatch.class);
            verify(declinedMatches).save(captor.capture());
            // save() on a composite key overwrites the existing row, so a gamer recycled
            // back into the feed and declined a second time gets a fresh thirty days
            // instead of expiring again on the next request.
            assertEquals(NOW.plus(Duration.ofDays(45)), captor.getValue().getDeclinedAt());
        }

        @Test
        @DisplayName("who-liked-you honours the same window")
        void admirersUseTheSameWindow() {
            when(gamerRepository.findPendingAdmirers(anyString())).thenReturn(List.of());

            matchService.getWhoLikedYou(gamer);

            ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
            verify(declinedMatches).findActiveExclusions(eq(gamer.getUserId()), since.capture());
            assertEquals(NOW.minus(Duration.ofDays(30)), since.getValue());
        }
    }
}
