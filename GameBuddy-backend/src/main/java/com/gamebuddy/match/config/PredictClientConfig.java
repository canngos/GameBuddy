package com.gamebuddy.match.config;

import com.gamebuddy.common.http.HttpServiceClients;
import com.gamebuddy.match.domain.client.PredictClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The one outbound HTTP client left in the application.
 *
 * <p>Everything else that used to be a network call between services — notifications,
 * profile lookups, the friend and match checks — is a method call now. What survives is
 * the Python recommendation model, because it is a different runtime and cannot be folded
 * into a JVM process.
 *
 * <p>Even this should eventually go: precomputing recommendations during the training run
 * and writing them to Postgres would take the model off the swipe path entirely, leaving
 * the request with no outbound call at all.
 */
@Configuration
public class PredictClientConfig {

    @Bean
    public PredictClient predictClient(Environment environment) {
        return HttpServiceClients.create(PredictClient.class, environment, "gamebuddy.predict");
    }
}
