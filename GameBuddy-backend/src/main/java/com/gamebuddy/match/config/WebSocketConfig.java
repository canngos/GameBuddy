package com.gamebuddy.match.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket setup for the chat.
 *
 * <p>Two changes beyond the Boot 4 upgrade:
 *
 * <ul>
 *   <li>{@link StompAuthChannelInterceptor} is registered on the inbound channel, so a
 *       CONNECT frame without a valid token is refused. Nothing authenticated the socket
 *       before;
 *   <li>{@code setAllowedOrigins("*")} is replaced by a configured list. A wildcard let
 *       any web page on the internet open a socket to this service.
 * </ul>
 *
 * <p>The message converter is no longer configured by hand either — that override
 * installed a Jackson 2 {@code ObjectMapper}, which Boot 4 no longer uses. The defaults
 * pick up the application's own Jackson 3 configuration.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;

    @Value("${gamebuddy.websocket.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue", "/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Plain WebSocket. No SockJS.
     *
     * <p>SockJS was here and had to go. It is a compatibility layer for browsers that
     * cannot open a WebSocket, and <em>all</em> of its transports speak SockJS framing —
     * including the one at {@code /ws/websocket} that looks like plain WebSocket. A STOMP
     * client that speaks WebSocket natively connects, sends CONNECT, and then waits
     * forever: the frame is never parsed as STOMP, so nothing ever answers. It fails
     * silently, which is the worst way for a transport to fail.
     *
     * <p>Registering both on {@code /ws} does not fix it either — SockJS claims the path
     * and the plain handler never sees the upgrade.
     *
     * <p>Nothing needs SockJS. The client is React Native, which has real WebSocket
     * support; the web build is a development surface and every browser that can run it
     * has had WebSocket for over a decade.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
