package com.gamebuddy.notif.infrastructure.repository;

import com.gamebuddy.notif.infrastructure.entity.NotificationOutbox;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    /**
     * Claims a batch of notifications to deliver.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} — via the pessimistic write lock and the zero
     * timeout hint — is what makes this safe to run on more than one instance. Without it
     * two pollers would read the same rows and send everything twice; with a plain lock and
     * no skip they would queue behind each other and one would sit idle. Skipping locked
     * rows lets each poller take a different batch and get on with it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT o FROM NotificationOutbox o
            WHERE o.sentAt IS NULL
              AND o.nextAttemptAt <= :now
            ORDER BY o.createdAt ASC
            """)
    List<NotificationOutbox> claimPending(@Param("now") Instant now, Pageable pageable);

    /**
     * Drops delivered notifications older than the retention window.
     *
     * <p>The outbox is a queue, not a log. Anything already sent is only useful for a short
     * while afterwards, and left alone it would grow at the rate of every match and every
     * achievement forever.
     */
    @Modifying
    @Query("DELETE FROM NotificationOutbox o WHERE o.sentAt IS NOT NULL AND o.sentAt < :before")
    int deleteSentBefore(@Param("before") Instant before);

    /** Rows that exhausted their attempts, for whoever is watching. */
    long countBySentAtIsNullAndAttemptsGreaterThanEqual(int attempts);
}
