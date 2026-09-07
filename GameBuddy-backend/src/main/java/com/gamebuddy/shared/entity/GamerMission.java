package com.gamebuddy.shared.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One mission dealt to one gamer, in one slot of one set.
 *
 * <p>This table exists because the thing it replaced could not grow. Mission state used to
 * be five columns on {@code gamer}: one baseline per metric ({@code quest_base_messages},
 * {@code quest_base_matches}, {@code quest_base_lobbies}) and a bitmask of which of the
 * three had been paid. That shape could describe exactly three missions and no others — a
 * fourth needed a migration for its baseline, and because the mask was keyed on the enum's
 * {@code ordinal()}, reordering the catalogue silently reassigned everybody's claimed bits
 * mid-week. The migration that created those columns said as much at the time: *"Changing
 * the quest set is then a migration, which is the honest cost of changing what the game
 * asks people to do."*
 *
 * <p>Moving the baseline onto the assignment is what lets the pool be any size, because a
 * row can say which mission it belongs to.
 *
 * <h2>Rows are kept, not deleted</h2>
 *
 * <p>Three per set and a set is days of play, so a heavy account accumulates a few dozen
 * rows in a year. Keeping them buys two things worth more than that: the dealer can avoid
 * repeating a mission somebody has just seen, and there is a record of what was asked and
 * what it paid — which is the only way to answer "why did this account earn that much"
 * after the fact.
 *
 * @see com.gamebuddy.profile.domain.mission.Mission
 */
@Entity
@Table(name = "gamer_mission")
@IdClass(GamerMission.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class GamerMission implements Serializable {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** 1-based. Which deal this was, counting from the gamer's first ever. */
    @Id
    @Column(name = "set_index", nullable = false)
    private int setIndex;

    /** 0, 1 or 2 — the position on screen, so the three keep a stable order between loads. */
    @Id
    @Column(name = "slot", nullable = false)
    private short slot;

    @Column(name = "mission_code", nullable = false, length = 48)
    private String missionCode;

    /**
     * What the mission's metric read when this was dealt.
     *
     * <p>Progress is the metric now, minus this. Snapshotting at deal time rather than
     * storing progress directly means nothing has to be written when the gamer plays — the
     * counting systems that already exist for badges do all the work, and a mission is only
     * ever read.
     */
    @Column(name = "baseline", nullable = false)
    private int baseline;

    /**
     * Coins this pays, frozen at deal time.
     *
     * <p>Not looked up from {@code CoinEconomyProperties} on read. The band rates are config
     * so the owner can retune the curve without a release, and a retune must not change the
     * price of a mission somebody is already halfway through — a row that said 35 yesterday
     * and 25 today is indistinguishable from the app lying.
     */
    @Column(name = "reward", nullable = false)
    private int reward;

    /** When the coins were taken, or null while they are still waiting. */
    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    public GamerMission(String userId, int setIndex, short slot, String missionCode, int baseline, int reward) {
        this.userId = userId;
        this.setIndex = setIndex;
        this.slot = slot;
        this.missionCode = missionCode;
        this.baseline = baseline;
        this.reward = reward;
        this.assignedAt = Instant.now();
    }

    public boolean isClaimed() {
        return claimedAt != null;
    }

    /** Composite primary key: one row per slot per set per gamer. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String userId;
        private int setIndex;
        private short slot;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return setIndex == other.setIndex && slot == other.slot && Objects.equals(userId, other.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, setIndex, slot);
        }
    }
}
