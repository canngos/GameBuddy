package com.gamebuddy.moderation.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What a moderator did to an account, and why.
 *
 * <p>The audit trail, and the thing the terms promise: "we will tell you what rule was
 * broken". A ban used to be a boolean flipped on the gamer row with nothing beside it — no
 * reason, no author, no date — which is not something anyone can explain to the person
 * afterwards, or defend to a regulator.
 *
 * <p>One row per decision. A decision can carry a photo removal alongside it
 * ({@link #photoRemoved}) rather than being two visits, because "warn them and take the
 * picture down" is one judgement about one case.
 */
@Entity
@Table(
        name = "moderation_action",
        indexes = @Index(name = "idx_moderation_action_target", columnList = "target_id, created_at DESC"))
@Getter
@Setter
@NoArgsConstructor
public class ModerationAction {

    /**
     * The ladder. Each step is heavier than the last, and none of them is automatic — the
     * policy can hide a profile pending review, but only a person reaches this enum.
     */
    public enum Action {
        /** Nothing wrong. The reporters hear that; the target hears nothing. */
        DISMISS(null),
        /** A notice naming the rule. Nothing else changes. */
        WARN(null),
        /** The picture goes, and the notice says so. Nothing else changes. */
        REMOVE_PHOTO(null),
        SUSPEND_24H(Duration.ofHours(24)),
        SUSPEND_7D(Duration.ofDays(7)),
        /** Permanent. The only step that needs a written note. */
        BAN(null),
        /** Recorded so a lifted ban is as visible as the ban was. Never a case outcome. */
        UNBAN(null);

        private final Duration suspension;

        Action(Duration suspension) {
            this.suspension = suspension;
        }

        /** How long the account is blocked for, or null when this step does not block it. */
        public Duration suspension() {
            return suspension;
        }

        /** Whether the reports that led here were right. Everything but a dismissal. */
        public boolean upholds() {
            return this != DISMISS && this != UNBAN;
        }

        /** Whether the account is blocked as a result, for any length of time. */
        public boolean blocks() {
            return suspension != null || this == BAN;
        }
    }

    @Id
    private UUID id;

    /** Null for an action taken outside a case, such as a ban from the accounts tab. */
    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    /** The rule broken, in the reporters' vocabulary. Null on a dismissal. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 32)
    private ContentReport.ReasonCode reasonCode;

    @Column(length = 500)
    private String note;

    @Column(name = "photo_removed", nullable = false)
    private boolean photoRemoved = false;

    /** When a suspension ends; null for everything else. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
