package com.gamebuddy.moderation.infrastructure.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One person saying something about another: a profile, or a message they received.
 *
 * <p>The reporter is recorded. Without it there is no way to tell a genuine report from
 * someone mass-reporting a rival, and no way to weigh "five people reported this" against
 * "one person reported it five times" — the second of which the unique constraint below
 * prevents outright.
 *
 * <p>A report is evidence, not a decision. It joins the open {@link ModerationCase} against
 * the same person and is closed by whatever decides that case. What it carries is fixed at
 * the moment it is filed: the {@link #reasonCode}, the reporter's {@link #note}, and an
 * {@link #evidence} snapshot of what they were looking at — because the reported person
 * can change their picture or their name before anyone looks, and a report that only
 * points at ids would then show the moderator the clean version.
 */
@Entity
@Table(
        name = "content_report",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_report_once_per_reporter",
                        columnNames = {"content_type", "content_id", "reporter_id"}),
        indexes = {
            @Index(name = "idx_report_status", columnList = "status, createdAt"),
            @Index(name = "idx_content_report_case", columnList = "case_id")
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ContentReport {

    public enum ContentType {
        /** Retired with Communities. Rows survive as history; none can be filed. */
        POST,
        /** Retired with Communities. */
        COMMENT,
        /**
         * A gamer's profile itself — the picture, the name, what they wrote about
         * themselves.
         *
         * <p>For these the {@code contentId} is the reported gamer's own id, which is also
         * the {@code authorId}: a profile is the one piece of content whose author is the
         * content.
         */
        PROFILE,
        /**
         * A chat message, reported by the person who received it. {@code contentId} is the
         * message id and {@code authorId} the sender; {@link #roomId} and the evidence
         * snapshot carry the conversation around it.
         */
        MESSAGE
    }

    /**
     * Why. A fixed set rather than the text of a button, so it means the same thing in
     * every language and the policy can branch on it — an UNDERAGE report is urgent
     * whoever files it, and a SEXUAL report about a picture pulls the picture.
     */
    public enum ReasonCode {
        HARASSMENT,
        SEXUAL,
        SPAM_SCAM,
        UNDERAGE,
        IMPERSONATION,
        /** Needs the note: "other" with nothing written is not a report anyone can act on. */
        OTHER;

        /** Whether this reason, filed against a profile, is about the picture. */
        public boolean concernsThePicture() {
            return this == SEXUAL || this == UNDERAGE;
        }
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

    /**
     * The reporter's own words, or — for reports filed before reasons were structured —
     * the label of the button they tapped.
     */
    @Column(length = 500)
    private String reason;

    /** Null only on reports filed before reasons were structured. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 32)
    private ReasonCode reasonCode;

    /** What the reporter added, if anything; required when the reason is OTHER. */
    @Column(length = 300)
    private String note;

    /** The conversation a MESSAGE report came from; null for every other type. */
    @Column(name = "room_id")
    private UUID roomId;

    /**
     * A JSON snapshot of what was reported, taken when the report was filed. See
     * {@code ReportEvidence} for its shape.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String evidence;

    /** The case this report joined. Null only on rows older than cases. */
    @Column(name = "case_id")
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @CreationTimestamp
    private Instant createdAt;

    private String reviewedBy;
    private Instant reviewedAt;
}
