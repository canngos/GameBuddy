package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.GamerBadge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    @Modifying
    @Query("delete from GamerBadge b where b.userId = :userId")
    void deleteAllByUserId(String userId);
}
