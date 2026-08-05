package com.gamebuddy.community.infrastructure.entity;

import com.gamebuddy.shared.entity.Gamer;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "community")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Community implements Serializable {

    @Id
    private UUID communityId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    private String communityAvatar;
    private String wallpaper;

    @CreationTimestamp
    private Instant createdDate;

    /**
     * Deleting a community deletes its posts, and through them their comments.
     *
     * <p>There was no cascade, so {@code deleteCommunity} either failed on the
     * {@code post.community_id} foreign key or, without one, left every post in the
     * community orphaned and pointing at a row that no longer existed.
     */
    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<Post> posts = new HashSet<>();

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "community_members_join",
            joinColumns = @JoinColumn(name = "community_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<Gamer> members = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner", nullable = false)
    private Gamer owner;

    public boolean hasMember(Gamer gamer) {
        return members.contains(gamer);
    }

    public boolean isOwnedBy(Gamer gamer) {
        return owner != null && Objects.equals(owner.getUserId(), gamer.getUserId());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Community other && Objects.equals(communityId, other.communityId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(communityId);
    }
}
