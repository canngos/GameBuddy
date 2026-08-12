package com.gamebuddy.match.domain.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Delivery to sockets held by this process, and nothing else.
 *
 * <p>The default, and correct whenever exactly one instance is running — which is the
 * deployment this application is built for. Registered by {@code ChatMessagingConfig} only
 * when no Redis host is configured, so a single-box deployment and local development gain
 * neither a container to run nor a dependency that can be down.
 *
 * <p>Wrong the moment a second instance exists, silently: a send to somebody connected
 * elsewhere is indistinguishable from a send to somebody offline. That is what
 * {@link RedisUserMessaging} is for.
 */
@RequiredArgsConstructor
public class LocalUserMessaging implements UserMessaging {

    private final SimpMessagingTemplate template;

    @Override
    public void sendToUser(String principalName, String destination, Object payload) {
        template.convertAndSendToUser(principalName, destination, payload);
    }
}
