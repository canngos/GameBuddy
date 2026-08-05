package com.gamebuddy.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Backs {@code @Async} work — post-commit push notifications, the impression log — and the
 * scheduled sweeps that trim them.
 *
 * <p>Bounded on purpose: an unbounded queue would let a downstream outage accumulate
 * pending work until the JVM runs out of memory. Once the queue is full the caller runs the
 * task itself, which throttles the source of the work instead of hiding it.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean
    public Executor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.corePoolSize(2)
                .maxPoolSize(8)
                .queueCapacity(100)
                .threadNamePrefix("gamebuddy-async-")
                .customizers(
                        executor -> executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()))
                .build();
    }
}
