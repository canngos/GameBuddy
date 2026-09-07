package com.gamebuddy.match.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * The scheduler that drives the broker's STOMP heartbeats.
 *
 * <p><strong>A separate class on purpose.</strong> Spring builds its own
 * {@code messageBrokerTaskScheduler}, and using it looks like the obvious answer — but it
 * is created by the same configuration that collects {@code WebSocketMessageBrokerConfigurer}
 * beans, so asking for it from {@link WebSocketConfig} is a cycle. Field injection does not
 * help; the context still refuses to start. Declaring the bean here breaks the cycle
 * because nothing in this class depends on the broker.
 *
 * <p>It also has to be a bean rather than something {@code WebSocketConfig} constructs for
 * itself. A {@code ThreadPoolTaskScheduler} that is never initialised has no executor
 * behind it, and the symptom is silent: the broker still advertises
 * {@code heart-beat:10000,10000} in CONNECTED and then never sends one, so every client
 * concludes the connection is dead after 20s and reconnects forever. As a bean, Spring
 * initialises it — and shuts it down with the context, which a hand-rolled one would not be.
 */
@Configuration
public class WebSocketHeartbeatConfig {

    @Bean
    public TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // One thread is enough: the work per tick is writing a single newline per session.
        // Kept off the application's general-purpose scheduler so a slow job there cannot
        // delay a heartbeat and disconnect people.
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
