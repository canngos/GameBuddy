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

/**
 * A comment on a post.
 *
 * <p>The link to {@link Post} is new and is the point of this class's rewrite. A comment
 * was previously reachable only through {@code Post.comments}, a {@code @ManyToMany} over
 * a {@code post_comments_join} table — modelling a comment as something that can belong
 * to many posts, which it cannot. The practical consequence was that nothing holding a
 * comment could find its post, so its community, so its member list: every
 * comment-related endpoint had no way to perform an authorisation check even if it had
 * wanted to.
 */
@Entity
@Table(name = "comment")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Comment implements Serializable {

    @Id
    private UUID commentId;

    private String owner;
    private String message;

    @CreationTimestamp
    private Instant createdDate;

    @UpdateTimestamp
    private Instant updatedDate;

    /** Owning side. A comment belongs to exactly one post. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToMany
    @BatchSize(size = 50)
    @JoinTable(
            name = "comment_likes_join",
            joinColumns = @JoinColumn(name = "comment_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<Gamer> likes = new HashSet<>();

    /** Derived from {@link #likes}; never set by hand. */
    public int getLikeCount() {
        return likes.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Comment other && Objects.equals(commentId, other.commentId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(commentId);
    }
}
