package com.gamebuddy.admin.infrastructure.repository;

import com.gamebuddy.shared.entity.Gamer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The console's read-only view of the population.
 *
 * <p>A separate repository over the shared {@link Gamer} entity rather than more methods on
 * {@code GamerRepository}. Two reasons: these queries are the console's alone, and the
 * shared repository is explicitly the place for "what more than one module genuinely
 * needs"; and extending {@link Repository} rather than {@code JpaRepository} means this
 * exposes exactly three reads and no {@code save}, {@code delete} or {@code deleteAll}. A
 * dashboard that cannot write is a dashboard that cannot be turned into an incident.
 *
 * <p>Every method is one aggregate query. The obvious shape for a dashboard — a count call
 * per number on screen — is a dozen round trips that all scan the same table, and it grows
 * one query at a time as the page does.
 */
public interface AnalyticsRepository extends Repository<Gamer, String> {

    /**
     * Every headline number in one pass over {@code gamer}.
     *
     * <p>Deleted accounts are excluded from the counts they would distort but reported on
     * their own: an anonymised row is not a user, and folding it into the total would make
     * the population look like it never shrinks.
     *
     * <p>The moderator excludes itself from the totals — it is not a user of the product,
     * and a solo operator's dashboard reading "1 account" on launch day would be wrong in
     * the most demoralising possible way.
     */
    @Query(value = """
                    SELECT
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN')                  AS accounts,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND is_registered)                                          AS registered,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND last_active_at >= :dayAgo)                              AS active_today,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND last_active_at >= :weekAgo)                             AS active_week,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND last_active_at >= :monthAgo)                            AS active_month,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND created_date >= :dayAgo)                                AS new_today,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND created_date >= :weekAgo)                               AS new_week,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND is_blocked)                                             AS banned,
                      COUNT(*) FILTER (WHERE deleted_at IS NOT NULL)                                 AS deleted,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND subscription_tier <> 'BASIC'
                                         AND (subscription_expires_at IS NULL
                                              OR subscription_expires_at > :now))                    AS subscribers,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND avatar_status = 'PENDING')                              AS avatars_pending,
                      COUNT(*) FILTER (WHERE deleted_at IS NULL AND role <> 'ADMIN'
                                         AND COALESCE(age, 0) < 18)                                  AS minors
                    FROM gamer
                    """, nativeQuery = true)
    HeadlineCounts headline(
            @Param("now") Instant now,
            @Param("dayAgo") Instant dayAgo,
            @Param("weekAgo") Instant weekAgo,
            @Param("monthAgo") Instant monthAgo);

    /**
     * Signups per day, oldest first, for the growth graph.
     *
     * <p>Only days that had one are returned; the service fills the gaps. Doing it the
     * other way — a generated date series joined to the counts — pushes calendar
     * arithmetic into SQL for no benefit, and a day with no signups is a fact the caller
     * can derive.
     */
    @Query(value = """
                    SELECT CAST(created_date AS date) AS day, COUNT(*) AS signups
                    FROM gamer
                    WHERE created_date >= :since
                      AND role <> 'ADMIN'
                    GROUP BY CAST(created_date AS date)
                    ORDER BY day
                    """, nativeQuery = true)
    List<DailySignups> signupsSince(@Param("since") Instant since);

    /**
     * Mutual matches, counted once per pair.
     *
     * <p>{@code approved_matches} holds one row per direction, so a match is two rows and
     * the naive count doubles it. The self-join with {@code <} keeps one ordering of each
     * reciprocated pair.
     */
    @Query(value = """
                    SELECT COUNT(*) FROM approved_matches a
                    JOIN approved_matches b
                      ON b.user_id = a.matched_id AND b.matched_id = a.user_id
                    WHERE a.user_id < a.matched_id
                    """, nativeQuery = true)
    long countMutualMatches();

    /** Projection for {@link #headline}. Spring Data maps the column aliases by name. */
    interface HeadlineCounts {
        long getAccounts();

        long getRegistered();

        long getActiveToday();

        long getActiveWeek();

        long getActiveMonth();

        long getNewToday();

        long getNewWeek();

        long getBanned();

        long getDeleted();

        long getSubscribers();

        long getAvatarsPending();

        long getMinors();
    }

    /** One day of the growth series. */
    interface DailySignups {
        LocalDate getDay();

        long getSignups();
    }
}
