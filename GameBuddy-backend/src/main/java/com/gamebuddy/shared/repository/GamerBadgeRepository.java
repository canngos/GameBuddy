package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.GamerBadge;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Earned badges.
 *
 * <p>Shared rather than profile-private because deleting an account has to clear them and
 * that lives in {@code auth}, which must not reach into another module's repository.
 */
@Repository
public interface GamerBadgeRepository extends JpaRepository<GamerBadge, GamerBadge.Key> {

    /** Everything this gamer has earned. The badges screen composes it with the catalogue. */
    List<GamerBadge> findAllByUserId(String userId);

    /**
     * Just the ones on show, in slot order.
     *
     * <p>Used when rendering a profile — including somebody else's — so it deliberately
     * does not load the rest. A gamer with forty badges should not cost forty rows to
     * display three.
     */
    List<GamerBadge> findAllByUserIdAndShowcaseSlotIsNotNullOrderByShowcaseSlotAsc(String userId);

    long countByUserId(String userId);

    /** Whether anything is earned but unclaimed — the dot on the profile tab. */
    boolean existsByUserIdAndCollectedAtIsNull(String userId);

    /**
     * Marks a badge collected, once.
     *
     * <p>A conditional update rather than read-check-write. Two taps arriving together both
     * pass a "is it collected" check made a moment earlier, and both then pay — which was
     * survivable at 25 coins because the coin credit dragged the whole thing under
     * {@code Gamer}'s {@code @Version}, and is not survivable now that the hard tier pays
     * 125 or a frame that is not for sale. Zero rows back means somebody else got there
     * first.
     *
     * @return 1 if this call claimed the reward, 0 if it was already claimed
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
                    UPDATE gamer_badge
                       SET collected_at = :now
                     WHERE user_id = :userId
                       AND badge_code = :badgeCode
                       AND collected_at IS NULL
                    """, nativeQuery = true)
    int collect(@Param("userId") String userId, @Param("badgeCode") String badgeCode, @Param("now") Instant now);

    @Modifying
    @Query("delete from GamerBadge b where b.userId = :userId")
    void deleteAllByUserId(String userId);
}
