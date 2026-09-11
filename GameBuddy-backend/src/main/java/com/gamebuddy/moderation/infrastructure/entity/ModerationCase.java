package com.gamebuddy.moderation.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Everything currently said about one gamer, decided once.
 *
 * <p>A case rather than a queue of reports, because the question a moderator answers is
 * about a person, not a row: "is this account a problem?" Five reports against one account
 * are one decision, and deciding them one at a time — the shape the old queue had — meant
 * the second reviewer of the same person saw none of what the first had already read.
 *
 * <p>At most one case is open per target; that is a partial unique index in the schema,
 * not a convention. A report filed while one is open joins it. A report filed after it
 * closes opens a new one, and the closed ones are the person's history.
 *
 * <p>{@link #weightedScore} and {@link #distinctReporters} are what the automatic policy
 * reads. They are recomputed from the reports whenever one joins, and kept on the row so
 * the queue can sort by them without a join per case.
 */
@Entity
@Table(
        name = "moderation_case",
        indexes = {
            @Index(name = "idx_moderation_case_queue", columnList = "status, opened_at"),
            @Index(name = "idx_moderation_case_target", columnList = "target_id, opened_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor
public class ModerationCase {

    public enum Status {
        /** Waiting for a person, in arrival order. */
        OPEN,
        /**
         * Waiting for a person, ahead of everything OPEN. Set by the policy for an UNDERAGE
         * report or when enough distinct people have reported the same account that it was
         * hidden automatically.
         */
        URGENT,
        CLOSED
    }

    @Id
    private UUID id;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OPEN;

    @Column(name = "weighted_score", nullable = false, precision = 8, scale = 3)
    private BigDecimal weightedScore = BigDecimal.ZERO;

    @Column(name = "distinct_reporters", nullable = false)
    private int distinctReporters = 0;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "last_report_at", nullable = false)
    private Instant lastReportAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    /** The {@link ModerationAction.Action} that closed it, as its name; null while open. */
    @Column(length = 32)
    private String outcome;

    /**
     * True while the policy's automatic hide is in force on the target, so that the
     * decision — whatever it is — knows there is something to lift.
     */
    @Column(name = "auto_hidden", nullable = false)
    private boolean autoHidden = false;

    public boolean isOpen() {
        return status != Status.CLOSED;
    }
}
