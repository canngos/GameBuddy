package com.gamebuddy.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Credentials and URLs for Discord linking.
 *
 * <p>Everything here is optional, and the service checks before it offers a provider. A
 * developer clone with no Discord application registered should still start and still show
 * every other screen; what it must not do is send somebody to a consent page that answers
 * "invalid client". Configured providers are advertised, unconfigured ones are refused with
 * a clear code — see {@code DefaultAccountLinkService#startLink}.
 */
@Component
@ConfigurationProperties(prefix = "gamebuddy.link")
@Getter
@Setter
public class AccountLinkProperties {

    /**
     * Where this backend is reachable from a phone's browser.
     *
     * <p>Not derivable from the request. The callback URL has to be registered with Discord
     * ahead of time and byte-identical at the token exchange, so it is built from one
     * configured value rather than from whatever {@code Host} header arrived.
     *
     * <p>On the Android emulator this is {@code http://10.0.2.2:8080}: the browser is inside
     * the emulator, where {@code localhost} is the emulator itself and not the machine
     * running the backend.
     */
    private String publicBaseUrl = "http://10.0.2.2:8080";

    /** Where the callback sends the browser when it is done. The app's own URL scheme. */
    private String appReturnUrl = "gamebuddy://settings/linked";

    private Discord discord = new Discord();

    public boolean discordConfigured() {
        return notBlank(discord.clientId) && notBlank(discord.clientSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @Getter
    @Setter
    public static class Discord {
        private String clientId;

        /** Proves this server is the application the code was issued to. Never leaves it. */
        private String clientSecret;

        /** Where the consent screen lives. Configurable only so a test can point it elsewhere. */
        private String authorizeUrl = "https://discord.com/oauth2/authorize";
    }
}
