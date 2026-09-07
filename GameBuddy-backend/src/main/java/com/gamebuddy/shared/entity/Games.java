package com.gamebuddy.shared.entity;

import com.gamebuddy.common.enums.Platform;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** A game in the catalogue. Referenced by profiles, recommendations and communities. */
@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Games implements Serializable {

    @Id
    private String gameId;

    private String gameName;
    private String gameIcon;
    private String category;
    private Float avgVote;
    private String description;
    private Boolean isPopular;

    /**
     * Initialised rather than left null: the inverse side was null in one service's copy
     * and dereferencing it threw NullPointerException when a game had no players yet.
     */
    @ManyToMany(mappedBy = "likedgames")
    private Set<Gamer> gamers = new LinkedHashSet<>();

    /**
     * What this game is played on, so the picker can put a Switch owner's games first.
     *
     * <p>A set, like {@link Gamer#getPlatforms()}: almost everything worth listing is on
     * three platforms or five, and a single value would force a choice between filing
     * GTA V under PC and filing it under PlayStation, either of which is wrong for most of
     * the people who play it.
     *
     * <p><b>{@code @BatchSize} is load-bearing here, more than it is on {@code Gamer}.</b>
     * The catalogue endpoint returns every row — around 250 once the per-platform widening
     * has run — and a lazy collection without batching is one extra query per game, on the
     * screen every new account sees during onboarding. Batched, the same page costs five
     * queries. The number matches the fetch, not the collection: a game has at most five
     * platforms, so 50 is chosen to bound the round trips rather than the rows.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @CollectionTable(name = "game_platform", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "platform", length = 16, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Platform> platforms = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Games other)) {
            return false;
        }
        return gameId != null && gameId.equals(other.gameId);
    }

    @Override
    public int hashCode() {
        return gameId == null ? 0 : gameId.hashCode();
    }
}
