package com.gamebuddy.profile.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One verified external account, as shown on a profile.
 *
 * <p>What reaches a stranger is deliberately thin: which platform, and what the person is
 * called there. The provider's own id is never sent — it is the key the link is built on,
 * it identifies the account across every service that uses the same provider, and a profile
 * response has no reason to publish it.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LinkedAccountDto {

    /** {@code DISCORD}. The client owns the brand marks and the wording. */
    private String provider;

    /**
     * The display name, or null when it did not survive screening.
     *
     * <p>Null is a state the profile renders rather than hides: the badge still says the
     * account is verified, without putting a name on it. See {@code GamerLinkedAccount}.
     */
    private String handle;

    /**
     * {@code PUBLIC} or {@code MATCHES}. Own profile only.
     *
     * <p>Never sent for anybody else, for the same reason the email address is not: it is a
     * setting, not a fact about the person, and telling a stranger that a handle is
     * restricted tells them something about a choice that was made to keep them out.
     */
    private String visibility;
}
