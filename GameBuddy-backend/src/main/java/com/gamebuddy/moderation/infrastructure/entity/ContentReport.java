package com.gamebuddy.moderation.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A report against a post or a comment.
 *
 * <p>Only chat messages could be reported before. Posts and comments are the public,
 * many-to-many surfaces — the ones where abuse reaches an audience rather than one person
 * — and they had no report path at all, so the only remedy was for the author or an admin
 * to happen to notice.
 *
 * <p>The reporter is recorded. Without it there is no way to tell a genuine report from
 * someone mass-reporting a rival, and no way to weigh "five people reported this" against
 * "one person reported it five times" — the second of which the unique constraint below
 * now prevents outright.
 */
@Entity
@Table(
        name = "content_report",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_report_once_per_reporter",
                        columnNames = {"content_type", "content_id", "reporter_id"}),
        indexes = @Index(name = "idx_report_status", columnList = "status, createdAt"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ContentReport {

    public enum ContentType {
        POST,
        COMMENT,
        /**
         * A gamer's profile itself — the picture, the name, what they wrote about
         * themselves.
         *
         * <p>Not community content, and it sits here anyway. Reports have to arrive in one
         * queue or a moderator has to remember to check two, and the second one is the one
         * that goes unchecked. For these the {@code contentId} is the reported gamer's own
         * id, which is also the {@code authorId}: a profile is the one piece of content
         * whose author is the content.
         */
        PROFILE
    }

    public enum Status {
        /** Awaiting a moderator. */
        OPEN,
        /** Reviewed and the content was removed. */
        ACTIONED,
        /** Reviewed and judged acceptable. */
        DISMISSED
    }

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    /** Who wrote the reported content, denormalised so it survives the content's deletion. */
    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "reporter_id", nullable = false)
    private String reporterId;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @CreationTimestamp
    private Instant createdAt;

    private String reviewedBy;
    private Instant reviewedAt;
}
