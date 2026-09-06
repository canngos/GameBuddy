package com.gamebuddy.auth.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The two Discord calls a link needs: trade the code for a token, then ask who it belongs to.
 *
 * <p>Profile linking asks for {@code identify} alone — an id and a display name, no more.
 * Signing in asks for {@code identify email} as well, because an account has to be
 * reachable and because matching a verified address is what lets somebody who registered
 * with a password sign in with Discord and land on the same account rather than a second
 * one. Nothing else is requested in either flow: a scope granted is a scope somebody has
 * to trust us with.
 */
@HttpExchange
public interface DiscordClient {

    /**
     * Exchanges the authorisation code for an access token.
     *
     * <p>Form-encoded rather than JSON because OAuth 2.0 says so, and the client secret
     * travels in the body rather than in a header for the same reason: it is the shape
     * Discord documents.
     */
    @PostExchange(value = "/oauth2/token", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    TokenResponse exchangeCode(@RequestBody MultiValueMap<String, String> form);

    /**
     * The account behind the token.
     *
     * @param authorization the {@code Bearer <token>} header, per request — this token
     *     belongs to one user and is thrown away immediately after, so it cannot be a
     *     default header on the client
     */
    @GetExchange("/users/@me")
    DiscordUser currentUser(@RequestHeader("Authorization") String authorization);

    /**
     * What we take from the token response, which is one field.
     *
     * <p>The refresh token is deliberately not mapped. Nothing here refreshes: identity is
     * the whole point of the link, we read it once, and a stored OAuth token would be a
     * credential worth stealing that buys the feature nothing. Re-reading a renamed handle
     * costs the user one tap through a consent screen they have already seen.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    /**
     * A Discord account.
     *
     * @param id the snowflake — permanent, and what both the link and the sign-in are keyed
     *     on. Never the email: people change the address on a Discord account and keep it.
     * @param username the unique handle, {@code name} in the new username system
     * @param globalName the chosen display name, which may be null and is what Discord
     *     shows people. Preferred for display; {@link #username} is the fallback, because a
     *     display name is what somebody recognises and the handle is what they search for.
     * @param email present only when the {@code email} scope was granted, so null on every
     *     profile-linking round trip and populated on every sign-in one
     * @param verified whether Discord vouches for that address. Null is read as false: the
     *     only safe reading of a missing answer is that nobody has proved the mailbox.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DiscordUser(
            String id,
            String username,
            @JsonProperty("global_name") String globalName,
            String email,
            Boolean verified) {}
}
