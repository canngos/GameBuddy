package com.gamebuddy.common.http;

import com.gamebuddy.common.security.InternalApiKeyFilter;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Builds {@code @HttpExchange} clients over the JDK's own {@link HttpClient}.
 *
 * <p>Replaces Spring Cloud OpenFeign across the services: Feign dragged in the whole
 * Spring Cloud release train — with its own Boot-version compatibility matrix to keep
 * satisfied — to issue a handful of requests that {@code RestClient} makes natively.
 * No Apache HttpClient or OkHttp dependency either; the JDK client is enough.
 */
public final class HttpServiceClients {

    private HttpServiceClients() {}

    /**
     * Binds {@code <prefix>.url / .connect-timeout / .read-timeout} and builds the client.
     *
     * <p>Bound through {@link Binder} rather than {@code @ConfigurationProperties} on a
     * {@code @Bean} method, because that path uses JavaBean binding and cannot populate
     * a record. This keeps one shared properties type instead of a near-identical
     * mutable class per service.
     */
    public static <T> T create(Class<T> clientInterface, Environment environment, String prefix) {
        DownstreamProperties properties = Binder.get(environment)
                .bind(prefix, DownstreamProperties.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing downstream configuration under '" + prefix + "'. Set " + prefix + ".url."));
        return create(clientInterface, properties);
    }

    public static <T> T create(Class<T> clientInterface, DownstreamProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                // HTTP/1.1 explicitly. The JDK client defaults to HTTP_2, which means every
                // new connection opens with an h2c upgrade attempt — and the model service
                // is uvicorn on h11, which speaks 1.1 only. It logs "Unsupported upgrade
                // request", and the exchange can then desynchronise into a 400 "Invalid HTTP
                // request received" that surfaces here as the model being unavailable.
                //
                // Nothing downstream benefits from HTTP/2 anyway: these are a handful of
                // small request/response calls over a private network, with none of the
                // multiplexing or header-compression wins that would justify the negotiation.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder builder =
                RestClient.builder().baseUrl(properties.url()).requestFactory(requestFactory);

        // Identifies this caller as a GameBuddy service on endpoints that only ever
        // serve other services; see InternalApiKeyFilter.
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder = builder.defaultHeader(InternalApiKeyFilter.HEADER, properties.apiKey());
        }
        RestClient restClient = builder.build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientInterface);
    }
}
