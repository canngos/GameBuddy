package com.gamebuddy.auth.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Every way into this account.
 *
 * <p>Read by the settings screen, which has to be able to say "you sign in with Google" and
 * to refuse the unlink that would lock somebody out. {@code hasPassword} belongs in the same
 * answer rather than a second call, because the two facts are only meaningful together: an
 * identity is safe to remove exactly when something else remains.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SocialIdentitiesResponseBody implements BaseModel {

    private List<Identity> identities;

    /** Whether an email-and-password sign-in is also possible. */
    private boolean hasPassword;

    /**
     * One identity.
     *
     * <p>The provider's subject is deliberately absent. It is the key the credential is built
     * on, it is of no use to the person looking at the screen, and a screen that displays it
     * is one screenshot away from publishing it.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Identity implements BaseModel {
        /** {@code GOOGLE} or {@code DISCORD}. The client owns the wording and the marks. */
        private String provider;

        /** What the provider said the address was when it was attached. May be null. */
        private String emailAtLink;

        private Instant createdAt;
    }
}
