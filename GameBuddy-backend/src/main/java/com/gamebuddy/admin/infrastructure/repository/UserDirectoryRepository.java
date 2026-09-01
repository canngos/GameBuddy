package com.gamebuddy.admin.infrastructure.repository;

import com.gamebuddy.shared.entity.Gamer;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Looking somebody up, for the console.
 *
 * <p>The one deliberate departure from {@code AnalyticsRepository}'s rule that nothing in
 * this module identifies a gamer. Aggregates are the right shape for a dashboard, and were
 * the right shape for everything the console did until it had to hand a promotion code to
 * particular people — "send this to the players who have not opened the app in a fortnight"
 * cannot be answered by a count. It is still read-only ({@link Repository}, not
 * {@code JpaRepository}, so there is no {@code save} or {@code delete} on it) and it still
 * never leaves the console: every caller is behind {@code hasRole('ADMIN')}.
 *
 * <p><b>Six queries rather than one with nullable parameters.</b> A bare parameter compared
 * against NULL leaves Postgres unable to infer its type — the trap {@code LobbyRepository}
 * documents for its four browse variants — and a dynamic criteria builder would be the
 * first in the codebase for the sake of five WHERE clauses. The search term is instead
 * always bound, as {@code %} when nothing was typed, which keeps one code path the way
 * lobby browsing keeps one by always binding {@code startsBefore}.
 *
 * <p>Every query shares {@link #LIVE}: not deleted, not banned, not staff, and finished
 * signing up. Those four are what "an account you could send something to" means, and the
 * same four the analytics counts use.
 */
public interface UserDirectoryRepository extends Repository<Gamer, String> {

    /**
     * Who counts as a person. Matches the predicate every analytics count uses, with
     * registration added: an account that never finished signing up has no username to
     * show and cannot be told about a gift.
     */
    String LIVE = "g.deletedAt IS NULL AND g.role <> com.gamebuddy.common.enums.Role.ADMIN"
            + " AND g.isBlocked = false AND g.isRegistered = true";

    /**
     * Prefix match on username or address.
     *
     * <p>Prefix rather than contains, because {@code lower(username)} is indexed and
     * {@code %term%} could not use it. Somebody looking a player up types the start of the
     * name they know.
     */
    String SEARCH = " AND (lower(g.gamerUsername) LIKE :q OR lower(g.email) LIKE :q)";

    /** Most recently seen first; accounts that have never been active sort last. */
    String ORDER = " ORDER BY g.lastActiveAt DESC NULLS LAST, g.createdDate DESC";

    @Query("SELECT g FROM Gamer g WHERE " + LIVE + SEARCH + ORDER)
    Page<Gamer> search(@Param("q") String q, Pageable pageable);

    /**
     * Dormant: last seen before the threshold.
     *
     * <p>An account with no {@code lastActiveAt} at all is not dormant here, matching what
     * the re-engagement job decided — never-active accounts are a different problem from
     * ones that drifted away, and a code is a poor answer to somebody who has not arrived.
     */
    @Query("SELECT g FROM Gamer g WHERE " + LIVE + SEARCH
            + " AND g.lastActiveAt IS NOT NULL AND g.lastActiveAt < :before" + ORDER)
    Page<Gamer> searchDormant(@Param("q") String q, @Param("before") Instant before, Pageable pageable);

    @Query("SELECT g FROM Gamer g WHERE " + LIVE + SEARCH
            + " AND g.subscriptionTier <> com.gamebuddy.common.enums.SubscriptionTier.BASIC"
            + " AND (g.subscriptionExpiresAt IS NULL OR g.subscriptionExpiresAt > :now)" + ORDER)
    Page<Gamer> searchGold(@Param("q") String q, @Param("now") Instant now, Pageable pageable);

    /** The complement of {@link #searchGold}: everybody a Gold code would be new to. */
    @Query("SELECT g FROM Gamer g WHERE " + LIVE + SEARCH
            + " AND (g.subscriptionTier = com.gamebuddy.common.enums.SubscriptionTier.BASIC"
            + "      OR (g.subscriptionExpiresAt IS NOT NULL AND g.subscriptionExpiresAt <= :now))" + ORDER)
    Page<Gamer> searchFree(@Param("q") String q, @Param("now") Instant now, Pageable pageable);

    @Query("SELECT g FROM Gamer g WHERE " + LIVE + SEARCH + " AND g.createdDate >= :since" + ORDER)
    Page<Gamer> searchNew(@Param("q") String q, @Param("since") Instant since, Pageable pageable);

    /**
     * Restricted to an explicit set of ids.
     *
     * <p>How the "reports upheld" filter is served: the ids come from the moderation
     * module's own service, because reports live there and a repository is module-private.
     * A join would have been shorter and would have reached across a boundary the build
     * enforces.
     */
    @Query("SELECT g FROM Gamer g WHERE " + LIVE + SEARCH + " AND g.userId IN :ids" + ORDER)
    Page<Gamer> searchWithin(@Param("q") String q, @Param("ids") Collection<String> ids, Pageable pageable);
}
