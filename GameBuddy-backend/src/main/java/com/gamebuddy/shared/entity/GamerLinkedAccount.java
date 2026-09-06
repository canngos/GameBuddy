package com.gamebuddy.shared.entity;

import com.gamebuddy.common.enums.LinkVisibility;
import com.gamebuddy.common.enums.LinkedProvider;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Discord account this gamer has proved they own.
 *
 * <p>Its own entity rather than two columns on {@link Gamer}, for the same reason
 * {@code gamer_platform} is its own table: a gamer holds a set of these, each carrying its
 * own metadata — when it was linked, who may see it, when the handle was last read. Two
 * providers would be four columns on an entity that already has more than sixty, and a
 * third would be two more.
 *
 * <p><strong>{@link #externalId} is the identity; {@link #handle} is only its label.</strong>
 * People rename themselves on both platforms and the id never changes, so everything that
 * has to be stable — the uniqueness constraint, recognising a re-link as the same account —
 * hangs off the id. The handle is a cache of what the provider last called them.
 *
 * <p>The handle is nullable, and that is a real state rather than an oversight: a handle
 * refused by the profanity filter is not stored, and the profile shows a verified badge
 * with no name. Masking it would put {@code D***head} on a profile, which is not what the
 * person is called and reads as the app inventing a name for them.
 */
@Entity
@Table(
        name = "gamer_linked_account",
        indexes = {
            @Index(name = "idx_gamer_linked_account_external", columnList = "provider, external_id", unique = true)
        })
@IdClass(GamerLinkedAccount.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class GamerLinkedAccount {

    /**
     * The owning gamer.
     *
     * <p>Mapped as the association rather than a bare id column so a link can be read from
     * a {@code Gamer} without a second lookup, and so the module that writes one does not
     * have to hold a repository for the other side.
     */
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Gamer gamer;

    /**
     * Which platform this is.
     *
     * <p>Part of the primary key, so one gamer holds at most one account per provider.
     * Linking a second Discord account replaces the first rather than accumulating —
     * "my Discord" is singular to the person doing it.
     *
     * <p>{@code EnumType.STRING}. An ordinal column would silently re-label every row if a
     * provider were ever inserted above another in the declaration.
     */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 16, nullable = false)
    private LinkedProvider provider;

    /**
     * The provider's own identifier: a Discord snowflake.
     *
     * <p>Unique per provider across every account here — see the index above. Without that
     * constraint the OAuth round-trip would prove ownership once and then let the same
     * proof be replayed onto every account somebody controls, which is impersonation with
     * extra steps and would make the whole verification pointless.
     */
    @Column(name = "external_id", length = 64, nullable = false)
    private String externalId;

    /** The display name the provider last gave us, or null if it did not survive screening. */
    @Column(name = "handle")
    private String handle;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 16, nullable = false)
    private LinkVisibility visibility = LinkVisibility.MATCHES;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    /** When {@link #handle} was last read from the provider. Null if it has never been read. */
    @Column(name = "handle_refreshed_at")
    private Instant handleRefreshedAt;

    /** Whether {@code viewer} may see this handle, given their relationship to the owner. */
    public boolean isVisibleTo(Gamer owner, Gamer viewer) {
        if (owner.getUserId().equals(viewer.getUserId())) {
            return true;
        }
        if (visibility == LinkVisibility.PUBLIC) {
            return true;
        }
        return owner.isMatchedWith(viewer) || owner.getFriends().contains(viewer);
    }

    /** Composite key: one link per gamer per provider. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {

        private String gamer;
        private LinkedProvider provider;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(gamer, key.gamer) && provider == key.provider;
        }

        @Override
        public int hashCode() {
            return Objects.hash(gamer, provider);
        }
    }
}
