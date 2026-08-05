package com.gamebuddy.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when no R2 access key is configured, i.e. uploads go to the local disk.
 *
 * <p>A hand-written condition rather than {@code @ConditionalOnProperty} because that
 * annotation treats an empty value as present, and {@code R2_ACCESS_KEY_ID=} with nothing
 * after it — exactly what an unconfigured {@code .env} produces — would therefore match as
 * "R2 configured". Blank has to mean absent here.
 */
public class LocalStorageCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty("gamebuddy.storage.r2.access-key-id");
        return key == null || key.isBlank();
    }
}
