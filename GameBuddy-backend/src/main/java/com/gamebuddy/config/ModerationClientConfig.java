package com.gamebuddy.config;

import com.gamebuddy.common.http.HttpServiceClients;
import com.gamebuddy.shared.moderation.ImageModerationClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The screening client, pointed at the same Python service as the recommender.
 *
 * <p>Same process, different endpoint: the model service already exists, already holds a
 * shared secret with this one, and already sits on the private network. Standing up a
 * second service for one classifier would buy nothing.
 *
 * <p>Its own timeouts, though — under {@code gamebuddy.moderation} rather than
 * {@code gamebuddy.predict}. Classification takes hundreds of milliseconds against a
 * recommendation's single digits, and sharing the recommender's read timeout would mean
 * every upload times out while the deck is tuned for speed.
 */
@Configuration
public class ModerationClientConfig {

    @Bean
    public ImageModerationClient imageModerationClient(Environment environment) {
        return HttpServiceClients.create(ImageModerationClient.class, environment, "gamebuddy.moderation");
    }
}
