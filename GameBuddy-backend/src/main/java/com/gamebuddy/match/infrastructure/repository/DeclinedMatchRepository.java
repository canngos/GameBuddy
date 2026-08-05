package com.gamebuddy.match.infrastructure.repository;

import com.gamebuddy.match.infrastructure.entity.DeclinedMatch;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeclinedMatchRepository extends JpaRepository<DeclinedMatch, DeclinedMatch.Key> {

    /**
     * Who this gamer has declined recently enough to still be hidden.
     *
     * <p>Ids rather than entities: the caller only needs to exclude them, and loading a
     * hundred {@code Gamer} rows to read their ids would be a page of pointless queries.
     */
    @Query("SELECT d.declinedId FROM DeclinedMatch d WHERE d.userId = :userId AND d.declinedAt > :since")
    List<String> findActiveExclusions(@Param("userId") String userId, @Param("since") Instant since);

    /**
     * Clears a decline, so accepting someone previously passed over works.
     *
     * <p>A gamer can only be in one state at a time. Leaving the old decline in place would
     * mean both sets contained the same person and the two would disagree about what the
     * gamer had decided.
     */
    @Modifying
    @Query("DELETE FROM DeclinedMatch d WHERE d.userId = :userId AND d.declinedId = :declinedId")
    int clear(@Param("userId") String userId, @Param("declinedId") String declinedId);

    /**
     * Drops expired declines.
     *
     * <p>Not strictly required — the window is applied on read — but a table that only ever
     * grows is a table that eventually needs an outage to fix.
     */
    @Modifying
    @Query("DELETE FROM DeclinedMatch d WHERE d.declinedAt < :before")
    int deleteDeclinedBefore(@Param("before") Instant before);

    /** Erases a deleted gamer's declines, in both directions. */
    @Modifying
    @Query("DELETE FROM DeclinedMatch d WHERE d.userId = :userId OR d.declinedId = :userId")
    int deleteAllInvolving(@Param("userId") String userId);
}
