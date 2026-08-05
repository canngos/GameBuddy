package com.gamebuddy.match.infrastructure.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One candidate shown to one gamer, at one position on the page.
 *
 * <p>Nothing previously connected a recommendation to what happened to it. Decisions were
 * recorded — {@code approved_matches} and {@code declined_matches} — but not what was
 * offered, so it was impossible to ask the only question that matters about a recommender:
 * of the people we chose to show, how many did the gamer actually want? Without that the
 * model's hyperparameters stay frozen at values tuned on synthetic data and a degradation
 * in quality is invisible.
 *
 * <p>Joining this table against the decision tables on {@code (user_id, candidate_id)}
 * yields the shown → swiped → matched funnel the training job needs.
 *
 * <p>Two things this table needs before it has been running long: a retention policy, since
 * it grows by up to one row per candidate per feed open, and inclusion in account deletion,
 * because it is behavioural data about identifiable users.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "recommendation_impression",
        indexes = {
            // The training job reads a window of impressions per gamer and joins on the
            // candidate; retention deletes scan by date.
            @Index(name = "idx_impression_user_candidate", columnList = "user_id, candidate_id"),
            @Index(name = "idx_impression_served_at", columnList = "served_at")
        })
public class RecommendationImpression implements Serializable {

    /**
     * Assigned in application code rather than by the database.
     *
     * <p>A page is written as one batch, and an IDENTITY column forces Hibernate to
     * round-trip per row to learn the generated key, which disables JDBC batching exactly
     * where it matters most.
     */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** The gamer who was shown the page. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** The gamer who appeared on it. */
    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    /** Zero-based position on the page. Rank is most of the signal in click data. */
    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private ImpressionSource source;

    @Column(name = "served_at", nullable = false)
    private Instant servedAt;
}
