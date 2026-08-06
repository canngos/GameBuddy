package com.gamebuddy.profile.infrastructure.repository;

import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Gamer;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The review queue's read side.
 *
 * <p>Module-private, and extending {@link Repository} rather than {@code JpaRepository} so
 * it offers these reads and nothing else. The write half of a review goes through the
 * shared {@code GamerRepository} like every other change to a gamer.
 */
public interface AvatarReviewRepository extends Repository<Gamer, String> {

    /**
     * Uploads waiting on a human, oldest first.
     *
     * <p>Oldest first because the queue is a queue: somebody whose picture has been
     * invisible for two days is the person most owed an answer. Deleted accounts are
     * excluded — there is nobody left to show the avatar to.
     */
    List<Gamer> findByAvatarStatusAndDeletedAtIsNullOrderByLastModifiedDateAsc(AvatarStatus status, Pageable pageable);

    /**
     * Uploads the classifier never answered about.
     *
     * <p>A null score is not a low score: it means the screening call failed, so nothing
     * has judged this image at all. These are re-screened automatically rather than shown
     * to a moderator — the queue would otherwise fill with ordinary photographs every time
     * the model container restarted, which is the failure that turns review into a chore.
     *
     * <p>A null {@code avatarKey} is excluded because there would be nothing to fetch.
     */
    @Query("""
            select g from Gamer g
            where g.avatarStatus = com.gamebuddy.shared.entity.AvatarStatus.PENDING
              and g.avatarScore is null
              and g.avatarKey is not null
              and g.deletedAt is null
            order by g.avatarUploadedAt asc nulls first
            """)
    List<Gamer> findUnscoredPending(Pageable pageable);

    /**
     * Ambiguous uploads that have waited longer than the deadline.
     *
     * <p>Scored, so re-screening them would only produce the same answer; a person is the
     * only thing that could change the outcome, and this finds the ones no person did.
     *
     * <p>{@code avatarUploadedAt is not null} matters: rows that predate the column have no
     * recorded arrival time, and treating a null as "long ago" would publish every historic
     * pending upload the first time this ran.
     */
    @Query("""
            select g from Gamer g
            where g.avatarStatus = com.gamebuddy.shared.entity.AvatarStatus.PENDING
              and g.avatarScore is not null
              and g.avatarKey is not null
              and g.deletedAt is null
              and g.avatarUploadedAt is not null
              and g.avatarUploadedAt < :deadline
            order by g.avatarUploadedAt asc
            """)
    List<Gamer> findScoredPendingBefore(@Param("deadline") Instant deadline, Pageable pageable);
}
