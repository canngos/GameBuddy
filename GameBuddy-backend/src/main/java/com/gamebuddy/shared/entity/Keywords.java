package com.gamebuddy.shared.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A playstyle tag: "competitive", "chill", "voice chat". Feeds the recommendation model. */
@Entity
@Table(name = "keywords")
@Getter
@Setter
@NoArgsConstructor
public class Keywords implements Serializable {

    @Id
    private UUID id;

    private String keywordName;
    private Instant createdDate;
    private String description;

    @ManyToMany(mappedBy = "keywords")
    private Set<Gamer> gamers = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Keywords other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
