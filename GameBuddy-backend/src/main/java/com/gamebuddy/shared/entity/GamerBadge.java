package com.gamebuddy.shared.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One badge a gamer has earned.
 *
 * <p>A row exists only once the mission is complete, so the presence of the row *is* the
 * earning. There is no "not yet earned" state stored anywhere; the badges screen composes
 * the catalogue with these rows and everything absent is locked.
 *
 * <p>{@code badgeCode} is a string rather than a foreign key to a catalogue table. The
 * catalogue is code — a mission is a rule, and a rule cannot live in a row — so the old
 * {@code achievements} table only ever held names for the rules to look themselves up by,
 * and the two could disagree. This way a badge that is deleted from the enum simply stops
 * appearing, and the row it left behind is inert rather than dangling.
 *
 * <p>Three columns, three separate ideas, deliberately not collapsed:
 *
 * <ul>
 *   <li>{@code earnedAt} — the mission was completed. Set once, never cleared.
 *   <li>{@code collectedAt} — the coins were claimed. Null means there is a reward
 *       waiting, which is what puts the dot on the profile tab.
 *   <li>{@code showcaseSlot} — 0, 1 or 2 if this is one of the three on show, else null.
 *       On the badge rather than on the gamer because only an earned badge can be
 *       showcased, and a nullable slot on the row that proves it makes that unstateable
 *       rather than merely checked.
 * </ul>
 */
@Entity
@Table(name = "gamer_badge")
@IdClass(GamerBadge.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class GamerBadge implements Serializable {

    /** How many badges a gamer may put on show. */
    public static final int SHOWCASE_SLOTS = 3;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Id
    @Column(name = "badge_code", nullable = false, length = 48)
    private String badgeCode;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt = Instant.now();

    /** When the coins were claimed, or null while they are still waiting. */
    @Column(name = "collected_at")
    private Instant collectedAt;

    /** 0..2 for a showcased badge, null otherwise. */
    @Column(name = "showcase_slot")
    private Integer showcaseSlot;

    public GamerBadge(String userId, String badgeCode) {
        this.userId = userId;
        this.badgeCode = badgeCode;
        this.earnedAt = Instant.now();
    }

    public boolean isCollected() {
        return collectedAt != null;
    }

    /** Composite primary key. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String userId;
        private String badgeCode;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(userId, other.userId) && Objects.equals(badgeCode, other.badgeCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, badgeCode);
        }
    }
}
