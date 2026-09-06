package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.interfaces.request.LinkVisibilityRequest;
import com.gamebuddy.auth.interfaces.response.LinkProvidersResponse;
import com.gamebuddy.auth.interfaces.response.LinkStartResponse;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;

/**
 * Linking a Discord account to a GameBuddy profile.
 *
 * <p>Split from {@link AuthService}, which is already a thousand lines, because this is a
 * self-contained flow with its own downstreams and its own failure modes. It lives in the
 * auth module rather than in profile because every other profile <em>write</em> is here too.
 *
 * <p>{@link #completeDiscordLink} returns a URL instead of a response body. Its caller is a
 * browser mid-redirect, not the app: it cannot read JSON and there is nobody to show it to.
 * It answers by sending the browser back into the app.
 */
public interface AccountLinkService {

    /**
     * Which providers this deployment has credentials for.
     *
     * <p>So the app can leave out what it cannot offer, rather than showing a button whose
     * only possible outcome is an error.
     */
    LinkProvidersResponse availableProviders();

    /** Mints a link ticket and returns the provider URL to open. */
    LinkStartResponse startLink(Gamer principal, String provider);

    /**
     * Finishes a Discord link.
     *
     * @param code the authorisation code, or null if the user pressed Cancel
     * @param state the link ticket, echoed by Discord
     * @return the deep link to send the browser to
     */
    String completeDiscordLink(String code, String state);

    DefaultMessageResponse unlink(Gamer principal, String provider);

    DefaultMessageResponse setVisibility(Gamer principal, String provider, LinkVisibilityRequest request);
}
