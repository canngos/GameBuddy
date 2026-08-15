package com.gamebuddy.moderation.infrastructure.repository;

import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {

    List<ContentReport> findAllByStatusOrderByCreatedAtAsc(ContentReport.Status status, Pageable pageable);

    boolean existsByContentTypeAndContentIdAndReporterId(
            ContentReport.ContentType contentType, UUID contentId, String reporterId);

    /** Every open report against one piece of content, resolved together when it is actioned. */
    List<ContentReport> findAllByContentTypeAndContentIdAndStatus(
            ContentReport.ContentType contentType, UUID contentId, ContentReport.Status status);

    /** How many distinct gamers have reported this author's content. Feeds a ban decision. */
    long countByAuthorIdAndStatus(String authorId, ContentReport.Status status);

    /** How much is waiting for a moderator. Read by the console's dashboard. */
    long countByStatus(ContentReport.Status status);

    /**
     * When the oldest still-open report arrived, or empty when the queue is clear.
     *
     * <p>Feeds the one number on the analytics screen that says whether the 24-hour
     * promise is currently being kept.
     */
    @Query("select min(r.createdAt) from ContentReport r where r.status = :status")
    Optional<Instant> oldestCreatedAt(@Param("status") ContentReport.Status status);
}
