package com.gamebuddy.config;

import com.gamebuddy.auth.infrastructure.client.DiscordClient;
import com.gamebuddy.common.http.HttpServiceClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The client behind account linking.
 *
 * <p>Its own timeouts, and they are the loosest in the application — this is the only
 * downstream that is neither on the private network nor under our control, and a user is
 * sitting on a "linking…" screen while it runs. A read timeout tuned for a service one hop
 * away would turn an ordinary slow moment at Discord into a failed link.
 */
@Configuration
public class AccountLinkClientConfig {

    @Bean
    public DiscordClient discordClient(Environment environment) {
        return HttpServiceClients.create(DiscordClient.class, environment, "gamebuddy.link.discord");
    }
}
