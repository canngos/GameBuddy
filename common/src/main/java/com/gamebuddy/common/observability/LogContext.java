package com.gamebuddy.common.observability;

import org.slf4j.MDC;

/**
 * The per-request fields every log line carries, and the only place their names are
 * written down.
 *
 * <p>The names are <a href="https://www.elastic.co/guide/en/ecs/current/index.html">Elastic
 * Common Schema</a> field names, and the dots in them are load-bearing. Boot's ECS
 * formatter nests dotted MDC keys, so {@code url.path} in the MDC becomes
 * {@code {"url": {"path": "..."}}} in the shipped document — the shape Kibana expects,
 * rather than a flat key with dots in it that has to be special-cased in every query.
 *
 * <p>Everything here is a {@link MDC} entry, so it attaches to <em>every</em> line a
 * request produces, not only the one the access log writes. That is the point: a stack
 * trace from deep inside a service is worth far more when it carries the request id, the
 * path and the user that produced it.
 */
public final class LogContext {

    /** Correlates every line of one request. Echoed back in the {@code X-Request-Id} header. */
    public static final String TRACE_ID = "trace.id";

    public static final String HTTP_METHOD = "http.request.method";
    public static final String URL_PATH = "url.path";
    public static final String CLIENT_IP = "client.ip";

    /**
     * The account's own identifier, never the e-mail address.
     *
     * <p>Logs get copied, shipped and kept for longer than anyone intends. An opaque id
     * answers "which user hit this bug" just as well as an address does, and does not turn
     * a log index into a mailing list.
     */
    public static final String USER_ID = "user.id";

    /** Emitted as a structured key-value pair on the access line, so it stays a number. */
    public static final String STATUS_CODE = "http.response.status_code";

    /**
     * ECS's own {@code event.duration} is nanoseconds, which is unreadable on a dashboard
     * and gets misread as microseconds at least once per project. Milliseconds, named as
     * such.
     */
    public static final String DURATION_MS = "event.duration_ms";

    private static final String[] REQUEST_KEYS = {TRACE_ID, HTTP_METHOD, URL_PATH, CLIENT_IP, USER_ID};

    private LogContext() {}

    /** Opens the context for one request. The user id arrives later, if at all. */
    public static void startRequest(String traceId, String method, String path, String clientIp) {
        MDC.put(TRACE_ID, traceId);
        MDC.put(HTTP_METHOD, method);
        MDC.put(URL_PATH, path);
        MDC.put(CLIENT_IP, clientIp);
    }

    /** The id of the request being served on this thread, or {@code null} outside one. */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    /**
     * The caller's address for this request, or {@code null} outside one (a scheduled job, a
     * test). Resolved behind the proxy by {@code RequestLoggingFilter} — see it for why this
     * is the real client IP and not Caddy's.
     */
    public static String getClientIp() {
        return MDC.get(CLIENT_IP);
    }

    /**
     * Called once the request is authenticated, which is necessarily after the request has
     * already started logging — so the first few lines of a request carry no user id. That
     * is accurate rather than unfortunate: at that point nobody knows who is asking.
     */
    public static void setUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            MDC.put(USER_ID, userId);
        }
    }

    /**
     * Removes only what this class put there.
     *
     * <p>Not {@code MDC.clear()}: servlet threads are pooled and reused, and clearing the
     * whole map would also discard context owned by something outside this filter.
     */
    public static void clear() {
        for (String key : REQUEST_KEYS) {
            MDC.remove(key);
        }
    }
}
