package com.gamebuddy.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * What this deployment needs in order to offer social sign-in.
 *
 * <p>Optional, like {@link AccountLinkProperties} beside it and for the same reason: a
 * developer clone with no Google project registered has to start and run every other screen.
 * What it must not do is offer a button whose only possible outcome is an error, so
 * {@code GET /auth/social/providers} answers with what is actually configured and the app
 * draws only those.
 *
 * <p>Discord's credentials are <em>not</em> repeated here. It is the same OAuth application
 * as profile linking — same client id, same secret, a second redirect URI — and duplicating
 * the pair would create two places to rotate a secret and one of them to forget.
 */
@Component
@ConfigurationProperties(prefix = "gamebuddy.social")
@Getter
@Setter
public class SocialAuthProperties {

    private Google google = new Google();

    /**
     * Where the browser is sent when a Discord sign-in finishes.
     *
     * <p>Deliberately not {@code gamebuddy://settings/linked}: that screen is for a signed-in
     * gamer managing their profile, and this flow's whole premise is that there is no session
     * yet. See {@code app/social.tsx} in the client.
     */
    private String appReturnUrl = "gamebuddy://social";

    public boolean googleConfigured() {
        return notBlank(google.webClientId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @Getter
    @Setter
    public static class Google {
        /**
         * The <b>web</b> OAuth client id, which is what an ID token's {@code aud} carries.
         *
         * <p>Not the Android client id. Google mints the token on the device against the
         * Android client, and addresses it to the web client so that a backend can tell
         * tokens meant for it from tokens meant for any other application. Both must exist
         * in the Cloud console; only this one is checked here.
         *
         * <p>Public information — it ships inside the app too — so it is configuration
         * rather than a secret. Changing it orphans every stored Google subject, because
         * {@code sub} is stable per account <em>per project</em>.
         */
        private String webClientId;

        /** Google's published signing keys. Configurable only so a test can point it at a stub. */
        private String jwkSetUri = "https://www.googleapis.com/oauth2/v3/certs";
    }
}
