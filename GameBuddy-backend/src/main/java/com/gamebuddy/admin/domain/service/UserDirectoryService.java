package com.gamebuddy.admin.domain.service;

import com.gamebuddy.admin.infrastructure.repository.UserDirectoryRepository;
import com.gamebuddy.admin.interfaces.dto.UserDirectoryResponseBody;
import com.gamebuddy.admin.interfaces.dto.UserDirectoryResponseBody.DirectoryUser;
import com.gamebuddy.admin.interfaces.dto.UserIdsResponseBody;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.moderation.domain.service.ModerationService;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.storage.AvatarUrls;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finding the people a promotion code should go to.
 *
 * <p>Reaches the moderation module through its service interface rather than its
 * repository, the same way {@code AnalyticsService} asks it for the open report count. The
 * "upheld reporters" filter is a set of ids that only that module can produce, and asking
 * for it in this shape is what keeps the boundary the build enforces.
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    /**
     * A fortnight, matching the re-engagement job's second nudge.
     *
     * <p>Taken from there rather than picked: the app already decided that fourteen days
     * without opening it is the point at which somebody has drifted away, and a console
     * that used a different number would be describing a different group of people to
     * whoever reads both screens.
     */
    private static final Duration DORMANT_AFTER = Duration.ofDays(14);

    private static final Duration RECENTLY_JOINED = Duration.ofDays(7);

    /** The same ceiling a single send is capped at; see {@code PromoCodeRequest}. */
    public static final int MAX_SELECTION = 200;

    private final UserDirectoryRepository directory;
    private final ModerationService moderation;
    private final AvatarUrls avatars;
    private final Clock clock;

    /** One page of accounts matching a search term and a cohort. */
    @Transactional(readOnly = true)
    public UserDirectoryResponseBody search(String query, DirectoryFilter filter, Pageable pageable) {
        Page<Gamer> page = find(query, filter, pageable);

        // One query for every avatar on the page rather than one per row. The same
        // batching AvatarUrls exists to provide, and the reason a picker can show pictures
        // at all without becoming thirty round trips.
        Map<String, String> pictures = avatars.visibleTo(page.getContent());
        Instant now = clock.instant();

        List<DirectoryUser> users = page.getContent().stream()
                .map(gamer -> new DirectoryUser(
                        gamer.getUserId(),
                        gamer.getGamerUsername(),
                        gamer.getEmail(),
                        pictures.get(gamer.getUserId()),
                        gamer.getCreatedDate(),
                        gamer.getLastActiveAt(),
                        SubscriptionTier.effective(gamer.getSubscriptionTier(), gamer.getSubscriptionExpiresAt(), now)
                                == SubscriptionTier.GOLD))
                .toList();

        return new UserDirectoryResponseBody(users, page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }

    /**
     * Every account matching the same search, as ids, up to the cap.
     *
     * <p>Resolved on the server rather than by paging the whole list through the console:
     * "everybody who has not opened the app in a fortnight" is one query here and forty
     * round trips there, and the two would disagree about the answer the moment somebody
     * came back mid-scroll.
     */
    @Transactional(readOnly = true)
    public UserIdsResponseBody ids(String query, DirectoryFilter filter) {
        // One extra row, purely to find out whether the cap actually bit.
        Page<Gamer> page = find(query, filter, PageRequest.of(0, MAX_SELECTION + 1));
        List<Gamer> matched = page.getContent();
        boolean truncated = matched.size() > MAX_SELECTION;

        List<String> ids =
                matched.stream().limit(MAX_SELECTION).map(Gamer::getUserId).toList();
        return new UserIdsResponseBody(ids, truncated);
    }

    private Page<Gamer> find(String query, DirectoryFilter filter, Pageable pageable) {
        // Always bound, as '%' when nothing was typed, so there is one code path rather
        // than a nullable parameter Postgres cannot infer a type for.
        String q = query == null || query.isBlank() ? "%" : query.trim().toLowerCase() + "%";
        Instant now = clock.instant();

        return switch (filter) {
            case ALL -> directory.search(q, pageable);
            case OFFLINE_14D -> directory.searchDormant(q, now.minus(DORMANT_AFTER), pageable);
            case GOLD -> directory.searchGold(q, now, pageable);
            case FREE -> directory.searchFree(q, now, pageable);
            case NEW_7D -> directory.searchNew(q, now.minus(RECENTLY_JOINED), pageable);
            case REPORT_CONTRIBUTORS -> {
                Set<String> reporters = moderation.reporterIdsWithActionedReports();
                // An empty IN list is a SQL syntax error in some dialects and a query for
                // nothing in the rest. Answering directly is both correct and cheaper.
                yield reporters.isEmpty() ? Page.empty(pageable) : directory.searchWithin(q, reporters, pageable);
            }
        };
    }
}
