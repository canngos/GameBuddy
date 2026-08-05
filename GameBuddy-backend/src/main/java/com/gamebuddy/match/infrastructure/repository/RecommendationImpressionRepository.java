package com.gamebuddy.match.infrastructure.repository;

import com.gamebuddy.match.infrastructure.entity.RecommendationImpression;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationImpressionRepository extends JpaRepository<RecommendationImpression, UUID> {

    /**
     * Drops impressions older than the retention window.
     *
     * <p>This table grows by up to one row per candidate per feed open, so it is the
     * fastest-growing table in the system by a wide margin. The training job only ever
     * reads a recent window, so anything past it is cost without value.
     */
    @Modifying
    @Query("DELETE FROM RecommendationImpression i WHERE i.servedAt < :before")
    int deleteServedBefore(@Param("before") Instant before);

    /**
     * Erases one gamer's impression history, in both directions.
     *
     * <p>Needed for account deletion: this is behavioural data about identifiable people,
     * and a deleted account must not leave a record of who it was shown, nor of who it was
     * shown to.
     */
    @Modifying
    @Query("DELETE FROM RecommendationImpression i WHERE i.userId = :userId OR i.candidateId = :userId")
    int deleteAllInvolving(@Param("userId") String userId);
}
