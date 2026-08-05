package com.gamebuddy.shared.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
