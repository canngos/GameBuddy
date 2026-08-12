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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Used to decide whether a username is taken.
     *
     * <p>Case-insensitively, deliberately. The unique constraint on the column is not:
     * to the database {@code Sarah} and {@code sarah} are two different names, which is
     * exactly the pair someone would pick to be mistaken for somebody else.
     */
    Optional<Gamer> findByGamerUsernameIgnoreCase(String username);

    /** Used by push delivery to resolve a device token back to an account. */
    Optional<Gamer> findByFcmToken(String fcmToken);

    /** Admin: the banned list. */
    List<Gamer> findAllByIsBlockedTrue();

    /**
     * Recomputes the cached age for everyone whose birthday has passed since it was last
     * written. Native because {@code AGE()} has no JPQL equivalent.
     *
     * <p>The predicate restricts it to rows that are actually wrong, so on an ordinary
     * night this writes roughly one row in three hundred and sixty-five rather than
     * rewriting the whole table and churning a page of the index for nothing.
     */
    @Modifying
    @Query(value = """
                    UPDATE gamer
                    SET age = EXTRACT(YEAR FROM AGE(birth_date))
                    WHERE birth_date IS NOT NULL
                      AND (age IS NULL OR age <> EXTRACT(YEAR FROM AGE(birth_date)))
                    """, nativeQuery = true)
    int refreshAgesFromBirthDate();

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
     *
     * <p>The role predicate keeps the moderator out of the sample. {@code isPairableWith}
     * would drop them afterwards, so this is not the only guard — but filtering in Java
     * costs an exploration slot every time the moderator is drawn, which silently shortens
     * the page. See {@link Gamer#isDiscoverable()}.
     */
    @Query(value = """
                    SELECT * FROM gamer g
                    WHERE g.deleted_at IS NULL
                      AND g.is_blocked = false
                      AND g.role <> 'ADMIN'
                      AND (COALESCE(g.age, 0) < 18) = :minor
                      AND g.user_id <> ALL(CAST(:excluded AS varchar[]))
                    ORDER BY RANDOM()
                    LIMIT :limit
                    """, nativeQuery = true)
    List<Gamer> findRandomPairable(
            @Param("minor") boolean minor, @Param("excluded") String[] excluded, @Param("limit") int limit);

    /**
     * Gamers currently boosted in one country.
     *
     * <p>Ordered newest-boost-first so that when several people boost at once the one who
     * just paid is seen first — the money is most recent and the attention is worth most
     * while their thirty minutes are still running.
     *
     * <p>The age-band predicate mirrors {@code AgeBand} exactly, and a null age counts as a
     * minor: a boost must not be a way around the segregation every other path enforces.
     * Country is compared case-insensitively, matching the feed filter.
     */
    @Query(value = """
                    SELECT * FROM gamer g
                    WHERE g.boost_expires_at > :now
                      AND g.deleted_at IS NULL
                      AND g.is_blocked = false
                      AND g.role <> 'ADMIN'
                      AND (COALESCE(g.age, 0) < 18) = :minor
                      AND lower(g.country) = lower(CAST(:country AS varchar))
                      AND g.user_id <> ALL(CAST(:excluded AS varchar[]))
                    ORDER BY g.boost_expires_at DESC
                    LIMIT :limit
                    """, nativeQuery = true)
    List<Gamer> findBoosted(
            @Param("country") String country,
            @Param("minor") boolean minor,
            @Param("excluded") String[] excluded,
            @Param("now") Instant now,
            @Param("limit") int limit);

    /**
     * Everyone a narrowed feed must not show, so the model can rank <em>past</em> them.
     *
     * <p>Filtering the model's answer is not enough, and the reason is the same one that
     * made the feed run dry before declines were sent to the model: the model returns its
     * top N by similarity, and a filter applied afterwards can only remove from those N —
     * it can never reach the person who ranks 300th but is the only one online in your
     * country. Measured on the development population: 25 accounts active inside the
     * window, none of them in the 37 the model returned, so "online now" — the headline
     * Gold filter — returned an empty deck every time while 25 people sat there matching.
     *
     * <p>Returns the complement rather than the eligible set because the model's request
     * only carries an exclusion list. That makes the result grow with the population, not
     * with the answer, which is the wrong way round and is fine only while the population
     * is small: a country filter on a million accounts would ship most of them over the
     * wire. The fix when that day comes is an inclusion list in {@code PredictRequest},
     * not a bigger array here.
     *
     * <p>A null parameter disables its clause entirely — an unset filter must never
     * exclude anybody. Country is compared case-insensitively and a candidate with no
     * country recorded fails a country filter rather than passing it by accident.
     *
     * <p><b>Platform behaves the opposite way, and deliberately.</b> A candidate with no
     * platform recorded is <em>not</em> excluded, because an empty set means "has not said"
     * rather than "plays on something else" — every account created before the field
     * existed has one. The clause must stay the exact mirror of
     * {@code FeedFilters#matches}: if these two ever disagree, the deck and the count of it
     * disagree, and the bug looks like the feed randomly dropping people.
     */
    @Query(value = """
                    SELECT g.user_id FROM gamer g
                    WHERE (:gameId IS NOT NULL
                            AND NOT EXISTS (SELECT 1 FROM gamer_games_join j
                                             WHERE j.gamer_id = g.user_id AND j.game_id = :gameId))
                       OR (:country IS NOT NULL
                            AND (g.country IS NULL OR lower(g.country) <> lower(CAST(:country AS varchar))))
                       OR (CAST(:activeSince AS timestamptz) IS NOT NULL
                            AND (g.last_active_at IS NULL OR g.last_active_at < CAST(:activeSince AS timestamptz)))
                       OR (:platform IS NOT NULL
                            AND EXISTS (SELECT 1 FROM gamer_platform p WHERE p.user_id = g.user_id)
                            AND NOT EXISTS (SELECT 1 FROM gamer_platform p
                                             WHERE p.user_id = g.user_id AND p.platform = :platform))
                    """, nativeQuery = true)
    List<String> findIdsExcludedByFilters(
            @Param("gameId") String gameId,
            @Param("country") String country,
            @Param("activeSince") Instant activeSince,
            @Param("platform") String platform);

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
                      AND g.role <> 'ADMIN'
                    """, nativeQuery = true)
    List<Gamer> findPendingAdmirers(@Param("userId") String userId);

    /**
     * Whether this gamer has ever had a match answered in kind.
     *
     * <p>A match is mutual by definition, and {@code approved_matches} stores one row per
     * direction — so the question is whether a row exists whose mirror also exists. Asking
     * only "has this gamer swiped yes on anybody" would answer a different and much easier
     * question, and would be true of nearly every account by its second minute.
     *
     * <p>{@code EXISTS} rather than a count: the caller only ever asks whether the number
     * is above zero, and somebody with four hundred matches should not cost four hundred
     * rows to answer that.
     */
    @Query(value = """
                    SELECT EXISTS (
                      SELECT 1
                        FROM approved_matches a
                        JOIN approved_matches b
                          ON b.user_id = a.matched_id AND b.matched_id = a.user_id
                       WHERE a.user_id = :userId)
                    """, nativeQuery = true)
    boolean hasMutualMatch(@Param("userId") String userId);

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
     *
     * <p><b>The transaction is declared here, on the repository, and that placement is the
     * whole point.</b> It used to be declared on the calling filter, which could never
     * work: the filter is constructed with {@code new} in {@code ApplicationConfig} so
     * Spring never proxies it, and the call was a self-invocation besides. A
     * {@code @Modifying} query with no transaction throws, the caller swallowed it at
     * {@code debug}, and so this column was never written once — leaving re-engagement
     * nudges dormant and every retention figure reading a structural zero. A Spring Data
     * repository <em>is</em> a proxy, so the annotation takes effect here.
     *
     * <p>{@code REQUIRES_NEW} because this is bookkeeping attached to somebody else's
     * request: it must commit or fail on its own, never roll back the swipe that
     * triggered it.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update Gamer g set g.lastActiveAt = :now, g.nudgeCount = 0, g.lastNudgedAt = null "
            + "where g.userId = :userId")
    void touchLastActive(@Param("userId") String userId, @Param("now") Instant now);
}
