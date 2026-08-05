package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.Gamer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The one repository for {@link Gamer}, merged from the five each service declared.
 *
 * <p>Shared deliberately, and one of the few things that is. {@code Gamer} is the entity
 * every module needs — authentication resolves one, the profile screen renders one, the
 * feed ranks them — so hiding it behind a module would mean every module calling that
 * module for its most common read.
 *
 * <p>Queries that belong to one module's domain stay in that module's own repository
 * against its own entities. What lives here is what more than one module genuinely needs.
 */
@Repository
public interface GamerRepository extends JpaRepository<Gamer, String> {

    Optional<Gamer> findByEmail(String email);

    Optional<Gamer> findByGamerUsername(String username);

    /** Used by push delivery to resolve a device token back to an account. */
    Optional<Gamer> findByFcmToken(String fcmToken);

    /** Admin: the banned list. */
    List<Gamer> findAllByIsBlockedTrue();

    /**
     * Detaches a device token from every account except the one claiming it.
     *
     * <p>Two people sharing a phone, or one person reinstalling, left the token attached
     * to both accounts — so notifications for one were delivered to the other. The token
     * identifies a device, so exactly one account may hold it.
     */
    @Modifying
    @Query("update Gamer g set g.fcmToken = null where g.fcmToken = :token and g.userId <> :keepUserId")
    int clearFcmTokenFrom(@Param("token") String token, @Param("keepUserId") String keepUserId);

    /**
     * A random sample of gamers this one could legitimately be shown.
     *
     * <p>Backs the exploration slots in a recommendation page: the model ranks by
     * similarity alone, so a gamer nobody resembles is never surfaced, never liked, and —
     * because the desirability prior learns from likes per impression — sinks further.
     *
     * <p>The age-band predicate mirrors {@code AgeBand} exactly: a null age counts as a
     * minor, because failing open would put unknown ages in the adult pool.
     *
     * <p>{@code ORDER BY RANDOM()} scans the candidate set. Fine at launch scale, wants
     * revisiting past a few hundred thousand gamers.
     */
    @Query(value = """
                    SELECT * FROM gamer g
                    WHERE g.deleted_at IS NULL
                      AND g.is_blocked = false
                      AND (COALESCE(g.age, 0) < 18) = :minor
                      AND g.user_id <> ALL(CAST(:excluded AS varchar[]))
                    ORDER BY RANDOM()
                    LIMIT :limit
                    """, nativeQuery = true)
    List<Gamer> findRandomPairable(
            @Param("minor") boolean minor, @Param("excluded") String[] excluded, @Param("limit") int limit);

    /**
     * Gamers who have swiped yes on this one and are still waiting for an answer.
     *
     * <p>Reads the owning side of {@code approved_matches} directly. The inverse of a
     * {@code @ManyToMany} is only populated inside a Hibernate session, so walking it from
     * the other object silently returns nothing outside one — which is how a "who liked
     * you" list comes back empty in production while passing every test that happens to
     * run in a session.
     */
    @Query(value = """
                    SELECT g.* FROM gamer g
                    JOIN approved_matches m ON m.user_id = g.user_id
                    WHERE m.matched_id = :userId
                      AND g.deleted_at IS NULL
                      AND g.is_blocked = false
                    """, nativeQuery = true)
    List<Gamer> findPendingAdmirers(@Param("userId") String userId);

    /**
     * Gamers this one has sent a friend request to, still unanswered.
     *
     * <p>The mirror of {@code Gamer.waitingFriends}, which holds requests *received*. The
     * outgoing direction is the inverse of that mapping and so is not navigable from the
     * sender at all — the same trap as {@link #findPendingAdmirers}, and the same fix:
     * read the join table directly.
     *
     * <p>Note the column names read backwards. In {@code waiting_friends} the
     * {@code user_id} is the gamer who *received* the request and {@code requested_id} is
     * the one who sent it, which is what {@code acceptFriend} relies on when it checks
     * membership. Renaming them is a migration for another day; the query is written
     * against what the table means, not what it is called.
     */
    @Query(value = """
                    SELECT g.* FROM gamer g
                    JOIN waiting_friends w ON w.user_id = g.user_id
                    WHERE w.requested_id = :userId
                      AND g.deleted_at IS NULL
                    """, nativeQuery = true)
    List<Gamer> findRequestedByMe(@Param("userId") String userId);

    /**
     * Candidates for a "come back" notification.
     *
     * <p>Applies the cheap, population-shrinking rules only: idle since {@code idleBefore},
     * still under the per-absence cap, not nudged since {@code lastNudgeBefore}, reminders
     * not switched off, a device to send to, and an account that can still be used. Which
     * of the two thresholds a gamer has reached is decided in Java, where it reads as the
     * policy it is rather than as another pair of clauses here.
     *
     * <p>Ordered by how long they have been gone, so a capped run reaches the people most
     * at risk of never coming back first.
     */
    @Query(value = """
                    SELECT g.* FROM gamer g
                    WHERE g.last_active_at IS NOT NULL
                      AND g.last_active_at < :idleBefore
                      AND g.nudge_count < :maxNudges
                      AND (g.last_nudged_at IS NULL OR g.last_nudged_at < :lastNudgeBefore)
                      AND g.reminders_enabled = true
                      AND g.fcm_token IS NOT NULL
                      AND g.deleted_at IS NULL
                      AND g.is_blocked = false
                      AND g.is_registered = true
                    ORDER BY g.last_active_at ASC
                    LIMIT :limit
                    """, nativeQuery = true)
    List<Gamer> findNudgeable(
            @Param("idleBefore") Instant idleBefore,
            @Param("lastNudgeBefore") Instant lastNudgeBefore,
            @Param("maxNudges") int maxNudges,
            @Param("limit") int limit);

    /**
     * Marks a gamer as active now, and clears the nudge state because they came back.
     *
     * <p>A direct update rather than loading the entity and saving it: this runs on the
     * request path, it must not fight the {@code @Version} column with whatever else the
     * request happens to be doing, and it touches three columns nobody else writes.
     */
    @Modifying
    @Query("update Gamer g set g.lastActiveAt = :now, g.nudgeCount = 0, g.lastNudgedAt = null "
            + "where g.userId = :userId")
    void touchLastActive(@Param("userId") String userId, @Param("now") Instant now);
}
