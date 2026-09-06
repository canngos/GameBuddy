package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.interfaces.dto.SocialIdentitiesResponseBody;
import com.gamebuddy.auth.interfaces.dto.SocialSessionResponseBody;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;

/**
 * Signing in with Google or Discord.
 *
 * <p>Split from {@link AuthService} for the same reason {@link AccountLinkService} was: it is
 * a self-contained flow with its own downstreams and its own failure modes, and that file is
 * already a thousand lines.
 *
 * <p><b>Sign-in, not linking.</b> The neighbouring {@code AccountLinkService} proves that a
 * signed-in gamer owns a Discord account, and puts the handle on their profile. This one
 * answers a question asked before there is a session at all: which GameBuddy account, if
 * any, is this person — and if none, may we make one.
 */
public interface SocialAuthService {

    /** Which providers this deployment has credentials for, so the app draws only those. */
    java.util.List<String> availableProviders();

    /**
     * Signs in with a Google ID token obtained by the app's native account sheet.
     *
     * @param acceptedTerms whether the terms were agreed to. Only consulted when the flow
     *     would create an account; an existing gamer has already agreed and is not asked
     *     again.
     */
    SocialSessionResponseBody signInWithGoogle(String idToken, Boolean acceptedTerms);

    /** Mints a login ticket and returns the Discord URL to open in the system browser. */
    String startDiscordLogin();

    /**
     * Finishes a Discord sign-in.
     *
     * @param code the authorisation code, or null if the user pressed Cancel
     * @param state the outbound ticket, echoed by Discord
     * @return the deep link to send the browser to, carrying a fresh ticket on success
     */
    String completeDiscordLogin(String code, String state);

    /** Trades the ticket from the callback for a session. */
    SocialSessionResponseBody exchange(String ticket, Boolean acceptedTerms);

    /** What this gamer can sign in with, and whether a password is one of them. */
    SocialIdentitiesResponseBody identities(Gamer principal);

    /** Removes one way in. Refused when it is the last one and no password is set. */
    DefaultMessageResponse unlink(Gamer principal, String provider);
}
