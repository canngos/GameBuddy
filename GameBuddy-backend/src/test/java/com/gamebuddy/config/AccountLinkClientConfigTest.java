package com.gamebuddy.config;

import static org.junit.jupiter.api.Assertions.*;

import com.gamebuddy.auth.config.AccountLinkProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Proves the link client can actually be built from the shipped configuration.
 *
 * <p>This exists because of how the failure would otherwise present itself.
 * {@code HttpServiceClients.create} throws when {@code <prefix>.url} is missing, and it is
 * called from a {@code @Bean} method — so a mistyped or forgotten key does not fail a test,
 * it stops the whole application from starting, on the deploy, with every other feature
 * working perfectly in development where somebody happened to have the key set.
 *
 * <p>There is no {@code @SpringBootTest} anywhere in this codebase — the suite is slices, and
 * a full context would want a database — so the real {@code application.yml} is bound here the
 * same way {@code RateLimitBudgetsTest} binds it: through {@link Binder}, with the
 * {@code ${ENV:default}} placeholders resolved and no environment variables set. What comes
 * out is what a deployment that configures nothing gets.
 */
class AccountLinkClientConfigTest {

    private static final StandardEnvironment ENVIRONMENT = realApplicationYml();

    @Test
    @DisplayName("the client builds from application.yml with nothing configured")
    void clientsBuildFromShippedConfiguration() {
        AccountLinkClientConfig config = new AccountLinkClientConfig();

        // This throws IllegalStateException if its `url` key is missing. That is the whole
        // assertion: the application would not have started.
        assertNotNull(config.discordClient(ENVIRONMENT));
    }

    @Test
    @DisplayName("a clone with no credentials starts, and simply reports the provider unconfigured")
    void unconfiguredIsAValidState() {
        AccountLinkProperties properties = Binder.get(ENVIRONMENT)
                .bind("gamebuddy.link", Bindable.ofInstance(new AccountLinkProperties()))
                .orElseThrow(() -> new AssertionError("gamebuddy.link is missing from application.yml"));

        // Empty rather than absent: a fresh clone has no Discord application registered, and
        // that has to be a working state — every other screen must still run. The service
        // refuses linking with a clear code rather than sending somebody to a consent page
        // that answers "invalid client".
        assertFalse(properties.discordConfigured());

        // The two that must never be empty, because both are compared byte for byte against
        // what a provider echoes back.
        assertEquals("http://10.0.2.2:8080", properties.getPublicBaseUrl());
        assertEquals("gamebuddy://settings/linked", properties.getAppReturnUrl());
    }

    @Test
    @DisplayName("credentials switch the provider on")
    void configuredWhenCredentialsArePresent() {
        AccountLinkProperties properties = new AccountLinkProperties();
        assertFalse(properties.discordConfigured());

        properties.getDiscord().setClientId("id");
        // Half-configured is not configured: without the secret the token exchange cannot
        // happen, and the failure would land after the user had already granted consent.
        assertFalse(properties.discordConfigured());

        properties.getDiscord().setClientSecret("secret");
        assertTrue(properties.discordConfigured());
    }

    private static StandardEnvironment realApplicationYml() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        for (PropertySource<?> source : loadYaml()) {
            sources.addFirst(source);
        }
        ConfigurationPropertySources.attach(environment);
        // StandardEnvironment resolves the ${ENV:default} placeholders itself on read, and
        // no environment variables are set here — so what the binder sees is exactly what a
        // deployment that configures nothing gets, which is the case under test.
        return environment;
    }

    private static List<PropertySource<?>> loadYaml() {
        try {
            return new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
