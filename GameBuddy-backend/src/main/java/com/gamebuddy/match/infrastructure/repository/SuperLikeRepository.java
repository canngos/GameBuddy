package com.gamebuddy.match.infrastructure.repository;

import com.gamebuddy.match.infrastructure.entity.SuperLike;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SuperLikeRepository extends JpaRepository<SuperLike, SuperLike.Key> {

    /**
     * Which of these admirers super liked this gamer.
     *
     * <p>Scoped to the ids already on screen rather than fetching every super like anyone
     * ever sent them: the caller has just built a page of at most a few dozen admirers, and
     * this answers the question for exactly that page in one query.
     *
     * <p>Ids rather than entities, for the same reason {@code findActiveExclusions} returns
     * ids — the caller only needs to mark a boolean.
     */
    @Query("SELECT s.userId FROM SuperLike s WHERE s.targetId = :targetId AND s.userId IN :userIds")
    List<String> findSendersAmong(@Param("targetId") String targetId, @Param("userIds") Collection<String> userIds);

    /**
     * Forgets a super like, because the swipe that sent it was rewound.
     *
     * <p>Silent when there is no row: most rewinds undo an ordinary like, and a rewind that
     * finds nothing to delete here is the normal case rather than a problem.
     */
    @Modifying
    @Query("DELETE FROM SuperLike s WHERE s.userId = :userId AND s.targetId = :targetId")
    int clear(@Param("userId") String userId, @Param("targetId") String targetId);

    /**
     * Super likes this gamer has sent — the {@code SUPER_LIKES_SENT} metric.
     *
     * <p>The primary key is (userId, targetId), so this counts people rather than taps: a
     * second super like at the same person was never a second row. That is the honest
     * reading of the mission too — "super like five gamers", not "press it five times".
     */
    long countByUserId(String userId);
}
