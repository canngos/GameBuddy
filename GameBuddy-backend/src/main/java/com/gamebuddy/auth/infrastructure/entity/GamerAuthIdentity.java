package com.gamebuddy.auth.infrastructure.entity;

import com.gamebuddy.common.enums.AuthProvider;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An external identity that may sign in as this gamer.
 *
 * <p>A credential, not a profile field — which is the whole reason this is not a row in
 * {@code gamer_linked_account}. Nothing here is ever displayed, and the difference matters
 * at exactly one moment: the settings screen's "unlink" button reaches the linked-account
 * table freely, because removing a badge costs a badge. Removing the last row here, on an
 * account with no password, would lock somebody out. Two tables keep those two buttons from
 * ever being the same button.
 *
 * <p><b>Keyed by the provider's subject, never by the email.</b> People change the address on
 * a Google account and keep the account; matching on the address would hand the identity to
 * whoever inherited the old one. {@code emailAtLink} is kept for support and audit and is
 * never matched on again.
 *
 * <p>The composite id is {@code (userId, provider)} — one identity per provider per gamer —
 * with a unique index on {@code (provider, subject)} doing the load-bearing work: one Google
 * account cannot sign in as two gamers. Without that index, proving ownership once would let
 * the same proof be replayed against every account somebody cared to create.
 */
@Entity
@Table(
        name = "gamer_auth_identity",
        indexes = @Index(name = "idx_gamer_auth_identity_subject", columnList = "provider, subject", unique = true))
@IdClass(GamerAuthIdentity.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class GamerAuthIdentity {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private AuthProvider provider;

    /**
     * The provider's own identifier: a Google {@code sub}, a Discord snowflake.
     *
     * <p>Google's {@code sub} is stable per account <em>per OAuth project</em>, so swapping
     * the project's client id would orphan every row here. That is a migration, not a
     * configuration change, and it is the reason the client id is deployment configuration
     * rather than something a build may vary.
     */
    @Column(name = "subject", nullable = false)
    private String subject;

    /** What the provider said the address was at the time. Audit only; never matched on. */
    @Column(name = "email_at_link")
    private String emailAtLink;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Touched on every successful sign-in.
     *
     * <p>Answers "is this actually how they get in" when somebody writes in having lost one
     * of two methods — which is the difference between recovering an account and refusing to.
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /** The composite key. Public because JPA constructs it reflectively. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String userId;
        private AuthProvider provider;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(userId, key.userId) && provider == key.provider;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, provider);
        }
    }
}
