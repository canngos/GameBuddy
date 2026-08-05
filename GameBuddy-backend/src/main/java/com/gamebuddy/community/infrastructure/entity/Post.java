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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "post",
        indexes = {
            // Both are walked on every community feed request and neither was indexed.
            @Index(name = "idx_post_community", columnList = "community_id"),
            @Index(name = "idx_post_updated", columnList = "updatedDate")
        })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Post implements Serializable {

    @Id
    private UUID postId;

    private String owner;
    private String title;

    @Column(length = 4000)
    private String body;

    private String picture;

    @CreationTimestamp
    private Instant createdDate;

    @UpdateTimestamp
    private Instant updatedDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /**
     * Deleting a post deletes its comments.
     *
     * <p>Previously a {@code @ManyToMany} over {@code post_comments_join} with no
     * cascade, so {@code deletePost} left every comment row behind, unreachable and
     * counted by nothing.
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<Comment> comments = new HashSet<>();

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "post_likes_join",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<Gamer> likes = new HashSet<>();

    /**
     * Derived from {@link #likes} rather than stored.
     *
     * <p>The stored {@code like_count} column was written as {@code likes.size()} after
     * each change with no locking, so two concurrent likes both read the old set, both
     * wrote the same count, and one like vanished from the total while remaining in the
     * join table — the displayed count and the like list disagreed permanently.
     */
    public int getLikeCount() {
        return likes.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Post other && Objects.equals(postId, other.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(postId);
    }
}
