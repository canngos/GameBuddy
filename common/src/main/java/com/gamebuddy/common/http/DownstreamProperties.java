package com.gamebuddy.common.http;

import java.time.Duration;

/**
 * Connection settings for one downstream GameBuddy service.
 *
 * <p>The timeouts have defaults but no service may rely on an implicit "none": Feign's
 * defaults left both unset, so a downstream that accepted a connection and then stopped
 * responding would pin a request thread for as long as the socket stayed open.
 *
 * @param url base URL of the downstream service
 * @param connectTimeout how long to wait for the TCP/TLS handshake
 * @param readTimeout how long to wait for the response once connected
 * @param apiKey shared key sent as {@code X-Internal-Api-Key}; null when the downstream
 *     is not a GameBuddy service, as with the Python model
 */
public record DownstreamProperties(String url, Duration connectTimeout, Duration readTimeout, String apiKey) {

    public DownstreamProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
