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

    /**
     * The monetisation funnel, as far as the data allows.
     *
     * <p>One query rather than six, for the same reason the headline counts are one: they
     * are read together and a dashboard assembled from six moments disagrees with itself.
     *
     * <p>Every ratio is returned as its two counts rather than as a percentage. A rate with
     * a denominator of three is noise, and a screen that shows "33%" without showing the
     * three invites somebody to act on it — which in the first weeks after launch is
     * exactly when the denominators are smallest.
     */
    @Query(value = """
                    SELECT
                      (SELECT COUNT(DISTINCT user_id) FROM funnel_event
                        WHERE kind = 'PAYWALL_VIEWED' AND created_at >= :since)          AS paywall_viewers,
                      (SELECT COUNT(DISTINCT user_id) FROM funnel_event
                        WHERE kind = 'CHECKOUT_STARTED' AND created_at >= :since)        AS checkout_starters,

                      -- Trials and paid months are the same table; period_type is the only
                      -- thing that tells them apart.
                      (SELECT COUNT(DISTINCT user_id) FROM purchase
                        WHERE period_type = 'TRIAL' AND purchased_at >= :since)          AS trials_started,
                      (SELECT COUNT(DISTINCT user_id) FROM purchase
                        WHERE period_type IS DISTINCT FROM 'TRIAL'
                          AND status = 'GRANTED'
                          AND product_id LIKE 'gamebuddy.gold.%'
                          AND purchased_at >= :since)                                    AS paid_started,
                      (SELECT COUNT(DISTINCT user_id) FROM purchase
                        WHERE event_type = 'RENEWAL' AND purchased_at >= :since)         AS renewals,

                      -- Free to paid at 30 days: of the accounts that turned 30 days old in
                      -- the window, how many had ever bought Gold by then.
                      (SELECT COUNT(*) FROM gamer g
                        WHERE g.deleted_at IS NULL AND g.role <> 'ADMIN'
                          AND g.created_date <  :thirtyDaysAgo
                          AND g.created_date >= :sixtyDaysAgo)                           AS cohort30,
                      (SELECT COUNT(*) FROM gamer g
                        WHERE g.deleted_at IS NULL AND g.role <> 'ADMIN'
                          AND g.created_date <  :thirtyDaysAgo
                          AND g.created_date >= :sixtyDaysAgo
                          AND EXISTS (SELECT 1 FROM purchase p
                                       WHERE p.user_id = g.user_id
                                         AND p.product_id LIKE 'gamebuddy.gold.%'
                                         AND p.purchased_at < g.created_date + interval '30 days'))
                                                                                         AS cohort30Paid,

                      -- The economy, over the window. Earned and spent are kept apart
                      -- rather than netted: a net of zero is produced both by a healthy
                      -- economy and by one where nothing happens at all.
                      COALESCE((SELECT SUM(delta) FROM coin_ledger
                                 WHERE delta > 0 AND created_at >= :since), 0)           AS coins_earned,
                      COALESCE((SELECT -SUM(delta) FROM coin_ledger
                                 WHERE delta < 0 AND created_at >= :since), 0)           AS coins_spent
                    """, nativeQuery = true)
    FunnelCounts funnel(
            @Param("since") Instant since,
            @Param("thirtyDaysAgo") Instant thirtyDaysAgo,
            @Param("sixtyDaysAgo") Instant sixtyDaysAgo);

    /**
     * Day-7 retention, split by like-cap cohort.
     *
     * <p>The number the analysis says matters most in the first quarter, and the reason the
     * cohort column exists at all. Retained means active at least seven days after signing
     * up; only accounts old enough to have had the chance are counted, or every cohort
     * would be dragged down by yesterday's signups.
     */
    @Query(value = """
                    SELECT
                      COALESCE(like_cap_cohort, 'UNASSIGNED')                            AS cohort,
                      COUNT(*)                                                           AS signups,
                      COUNT(*) FILTER (WHERE last_active_at >= created_date + interval '7 days')
                                                                                         AS retained
                    FROM gamer
                    WHERE deleted_at IS NULL
                      AND role <> 'ADMIN'
                      AND created_date <= :cutoff
                      AND created_date >= :since
                    GROUP BY 1
                    ORDER BY 1
                    """, nativeQuery = true)
    List<CohortRetention> retentionByCohort(@Param("since") Instant since, @Param("cutoff") Instant cutoff);

    interface FunnelCounts {
        long getPaywallViewers();

        long getCheckoutStarters();

        long getTrialsStarted();

        long getPaidStarted();

        long getRenewals();

        long getCohort30();

        long getCohort30Paid();

        long getCoinsEarned();

        long getCoinsSpent();
    }

    interface CohortRetention {
        String getCohort();

        long getSignups();

        long getRetained();
    }

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
