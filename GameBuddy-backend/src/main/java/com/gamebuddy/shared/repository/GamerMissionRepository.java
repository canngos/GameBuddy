package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.GamerMission;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The missions a gamer has been dealt.
 *
 * <p>In {@code shared} rather than in profile because account deletion lives in {@code auth}
 * and has to be able to clear these — the same reason {@code GamerBadgeRepository} is here.
 */
@Repository
public interface GamerMissionRepository extends JpaRepository<GamerMission, GamerMission.Key> {

    /** The three on screen, in slot order so they do not shuffle between loads. */
    List<GamerMission> findAllByUserIdAndSetIndexOrderBySlotAsc(String userId, int setIndex);

    /**
     * Which missions this gamer has seen in the last {@code n} sets.
     *
     * <p>Used by the dealer to avoid handing somebody the same thing twice in a row once
     * the campaign is over and repeats are allowed. During the campaign every mission is
     * dealt exactly once, so this returns nothing the dealer did not already know.
     */
    @Query("select m.missionCode from GamerMission m where m.userId = :userId and m.setIndex > :after")
    List<String> findCodesDealtAfter(@Param("userId") String userId, @Param("after") int after);

    /** Every code this gamer has ever been dealt — the campaign's "already used" set. */
    @Query("select distinct m.missionCode from GamerMission m where m.userId = :userId")
    List<String> findAllCodesDealt(@Param("userId") String userId);

    /**
     * Claims one mission, once.
     *
     * <p>A conditional update rather than read-check-write, for the reason
     * {@code RewardedAdGrantRepository.claim} is one: two taps arriving together would
     * otherwise both find {@code claimed_at} null and both pay. Returning zero rows *is*
     * the answer "somebody else already had it", and the caller must not credit anything
     * when it does.
     *
     * <p>The {@code @Version} lock on {@code Gamer} would also have caught this, but only
     * by making one of the two requests fail with a conflict the gamer then has to
     * understand. This makes the second tap a no-op instead.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
                    UPDATE gamer_mission
                       SET claimed_at = :now
                     WHERE user_id = :userId
                       AND set_index = :setIndex
                       AND slot = :slot
                       AND claimed_at IS NULL
                    """, nativeQuery = true)
    int claim(
            @Param("userId") String userId,
            @Param("setIndex") int setIndex,
            @Param("slot") short slot,
            @Param("now") Instant now);

    @Modifying
    @Query("delete from GamerMission m where m.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);
}
