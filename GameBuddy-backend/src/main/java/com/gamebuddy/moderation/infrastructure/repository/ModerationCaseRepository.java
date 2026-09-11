package com.gamebuddy.moderation.infrastructure.repository;

import com.gamebuddy.moderation.infrastructure.entity.ModerationCase;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationCaseRepository extends JpaRepository<ModerationCase, UUID> {

    /**
     * The open case against one gamer, locked for the report that is about to join it.
     *
     * <p>Locked because two reports filed in the same second would otherwise both read
     * "no case yet" and both try to open one; the partial unique index would refuse the
     * second and turn a legitimate report into a 500. With the row lock, the second waits
     * and then sees the first's case.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ModerationCase c where c.targetId = :targetId and c.status <> 'CLOSED'")
    Optional<ModerationCase> findOpenForUpdate(@Param("targetId") String targetId);

    Optional<ModerationCase> findFirstByTargetIdAndStatusNot(String targetId, ModerationCase.Status status);

    /** The queue: urgent first, then oldest first. */
    @Query("""
            select c from ModerationCase c
            where c.status in :statuses
            order by case when c.status = 'URGENT' then 0 else 1 end, c.openedAt asc
            """)
    List<ModerationCase> queue(@Param("statuses") Collection<ModerationCase.Status> statuses, Pageable pageable);

    /** A gamer's closed cases, newest first: the history a moderator reads before deciding. */
    List<ModerationCase> findByTargetIdAndStatusOrderByOpenedAtDesc(
            String targetId, ModerationCase.Status status, Pageable pageable);

    long countByStatusIn(Collection<ModerationCase.Status> statuses);

    /** How many cases have waited the longest; feeds the console's SLA number. */
    @Query("select min(c.openedAt) from ModerationCase c where c.status <> 'CLOSED'")
    Optional<java.time.Instant> oldestOpenAt();
}
