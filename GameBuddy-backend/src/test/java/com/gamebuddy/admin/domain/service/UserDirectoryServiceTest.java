package com.gamebuddy.admin.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gamebuddy.admin.infrastructure.repository.UserDirectoryRepository;
import com.gamebuddy.admin.interfaces.dto.UserDirectoryResponseBody;
import com.gamebuddy.admin.interfaces.dto.UserIdsResponseBody;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.moderation.domain.service.ModerationService;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.storage.AvatarUrls;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Finding recipients for a promotion code.
 *
 * <p>Mostly about which query each filter picks, because that is the part a reader cannot
 * check by eye: six near-identical method names, and choosing the wrong one sends a
 * campaign to the wrong crowd without anything looking broken.
 */
@DisplayName("UserDirectoryService")
class UserDirectoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    private UserDirectoryRepository directory;
    private ModerationService moderation;
    private AvatarUrls avatars;
    private UserDirectoryService service;

    @BeforeEach
    void setUp() {
        directory = mock(UserDirectoryRepository.class);
        moderation = mock(ModerationService.class);
        avatars = mock(AvatarUrls.class);

        when(avatars.visibleTo(any(java.util.Collection.class))).thenReturn(Map.of());
        when(directory.search(anyString(), any())).thenReturn(Page.empty());
        when(directory.searchDormant(anyString(), any(), any())).thenReturn(Page.empty());
        when(directory.searchGold(anyString(), any(), any())).thenReturn(Page.empty());
        when(directory.searchFree(anyString(), any(), any())).thenReturn(Page.empty());
        when(directory.searchNew(anyString(), any(), any())).thenReturn(Page.empty());
        when(directory.searchWithin(anyString(), any(), any())).thenReturn(Page.empty());

        service = new UserDirectoryService(directory, moderation, avatars, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Gamer player(String id, String username) {
        Gamer gamer = new Gamer();
        gamer.setUserId(id);
        gamer.setGamerUsername(username);
        gamer.setEmail(username + "@example.com");
        gamer.setSubscriptionTier(SubscriptionTier.BASIC);
        return gamer;
    }

    @Test
    @DisplayName("an empty search matches everybody rather than nobody")
    void bindsAWildcardWhenNothingWasTyped() {
        service.search("  ", DirectoryFilter.ALL, PageRequest.of(0, 30));

        verify(directory).search(eq("%"), any(Pageable.class));
    }

    @Test
    @DisplayName("a search term becomes a lowercase prefix, because the index is on lower(username)")
    void bindsAPrefix() {
        service.search("Play", DirectoryFilter.ALL, PageRequest.of(0, 30));

        verify(directory).search(eq("play%"), any(Pageable.class));
    }

    @Test
    @DisplayName("dormant means the same fourteen days the re-engagement job means")
    void asksForTheSameFortnightTheNudgeJobUses() {
        service.search(null, DirectoryFilter.OFFLINE_14D, PageRequest.of(0, 30));

        ArgumentCaptor<Instant> before = ArgumentCaptor.forClass(Instant.class);
        verify(directory).searchDormant(eq("%"), before.capture(), any(Pageable.class));
        assertEquals(NOW.minus(Duration.ofDays(14)), before.getValue());
    }

    @Test
    @DisplayName("new means the last seven days")
    void asksForTheLastWeek() {
        service.search(null, DirectoryFilter.NEW_7D, PageRequest.of(0, 30));

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(directory).searchNew(eq("%"), since.capture(), any(Pageable.class));
        assertEquals(NOW.minus(Duration.ofDays(7)), since.getValue());
    }

    @Test
    @DisplayName("reporters come from the moderation module, never from a join")
    void asksModerationForReporters() {
        when(moderation.reporterIdsWithActionedReports()).thenReturn(Set.of("gamer-1", "gamer-2"));

        service.search(null, DirectoryFilter.REPORT_CONTRIBUTORS, PageRequest.of(0, 30));

        verify(directory).searchWithin(eq("%"), eq(Set.of("gamer-1", "gamer-2")), any(Pageable.class));
    }

    @Test
    @DisplayName("nobody has had a report upheld, so nothing is queried at all")
    void shortCircuitsAnEmptyCohort() {
        when(moderation.reporterIdsWithActionedReports()).thenReturn(Set.of());

        UserDirectoryResponseBody result =
                service.search(null, DirectoryFilter.REPORT_CONTRIBUTORS, PageRequest.of(0, 30));

        assertTrue(result.getUsers().isEmpty());
        // An empty IN list is a syntax error in some dialects and a pointless query in the
        // rest; answering directly is both correct and cheaper.
        verify(directory, never()).searchWithin(anyString(), any(), any());
    }

    @Test
    @DisplayName("membership is judged against the expiry, not the stored tier")
    void readsMembershipThroughTheExpiry() {
        Gamer lapsed = player("gamer-1", "lapsed");
        lapsed.setSubscriptionTier(SubscriptionTier.GOLD);
        lapsed.setSubscriptionExpiresAt(NOW.minus(Duration.ofDays(1)));

        Gamer member = player("gamer-2", "member");
        member.setSubscriptionTier(SubscriptionTier.GOLD);
        member.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(1)));

        when(directory.search(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(lapsed, member), PageRequest.of(0, 30), 2));

        List<UserDirectoryResponseBody.DirectoryUser> users =
                service.search(null, DirectoryFilter.ALL, PageRequest.of(0, 30)).getUsers();

        assertFalse(users.get(0).gold(), "a membership that ran out is not a membership");
        assertTrue(users.get(1).gold());
    }

    @Test
    @DisplayName("selecting everybody stops at the cap and says that it did")
    void reportsTruncation() {
        List<Gamer> many = IntStream.range(0, UserDirectoryService.MAX_SELECTION + 1)
                .mapToObj(index -> player("gamer-" + index, "player" + index))
                .toList();
        when(directory.search(anyString(), any())).thenReturn(new PageImpl<>(many));

        UserIdsResponseBody ids = service.ids(null, DirectoryFilter.ALL);

        assertEquals(UserDirectoryService.MAX_SELECTION, ids.getIds().size());
        assertTrue(ids.isTruncated(), "the console has to be told the selection was cut short");
    }

    @Test
    @DisplayName("a selection inside the cap is not reported as truncated")
    void reportsNoTruncationWhenEverybodyFits() {
        when(directory.search(anyString(), any())).thenReturn(new PageImpl<>(List.of(player("gamer-1", "player"))));

        UserIdsResponseBody ids = service.ids(null, DirectoryFilter.ALL);

        assertEquals(List.of("gamer-1"), ids.getIds());
        assertFalse(ids.isTruncated());
    }
}
