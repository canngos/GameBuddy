package com.gamebuddy.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One line per request, and a request id on every other line.
 *
 * <p>Before this existed the logs recorded what went wrong but not what was being asked
 * when it did. An {@code Unhandled exception} from the global handler named a class and a
 * line number; it did not say which endpoint, which user, or which of the twelve requests
 * in flight it belonged to. Reproducing a report meant guessing.
 *
 * <p>Now every request opens a {@link LogContext}: a trace id, the method, the path and
 * the client address go into the MDC, so they are attached to every line the request
 * produces — the access line below, an application {@code log.warn} four layers down, and
 * the stack trace if it fails. In Kibana that turns into
 * {@code trace.id: "…"}, which returns the whole request in order.
 *
 * <p>The trace id is also returned to the caller in {@code X-Request-Id}. A user reporting
 * "it failed at 14:32" is guesswork; a user quoting an id is a single query.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    /**
     * A name of its own rather than this class's.
     *
     * <p>Two reasons. It is one line per request and therefore the loudest logger in the
     * application, so it needs to be silenceable — {@code ACCESS_LOG_LEVEL=WARN} keeps only
     * the failures — without touching {@code LOG_LEVEL} and going quiet everywhere else.
     * And it is a stable name to filter on in Kibana: {@code log.logger: "gamebuddy.access"}
     * survives this class being moved or renamed.
     */
    public static final String LOGGER_NAME = "gamebuddy.access";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    /** Accepted on the way in and always sent on the way out. */
    public static final String TRACE_ID_HEADER = "X-Request-Id";

    /**
     * Must run after Boot's {@code ForwardedHeaderFilter}, which is registered at
     * {@link Ordered#HIGHEST_PRECEDENCE}. Otherwise {@code client.ip} would be the proxy's
     * address on every request behind one — the same value for everybody, which is the same
     * as having no value at all.
     */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    /**
     * A client-supplied id is convenient — it lets a mobile client stitch its own logs to
     * the server's — but it is attacker-controlled text heading straight into a log
     * pipeline. Anything that is not a short, boring token is replaced with a generated
     * one rather than rejected: the request is fine, only the label is not.
     */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * Polled by the container healthcheck every ten seconds, forever. At INFO these alone
     * would be the majority of the index and would push out logs someone might actually
     * read, so they drop to DEBUG — still there when explicitly asked for.
     */
    private static final List<String> QUIET_PATHS = List.of("/actuator/health", "/actuator/info");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        String method = request.getMethod();
        String path = request.getRequestURI();

        LogContext.startRequest(traceId, method, path, request.getRemoteAddr());

        // Set before the chain runs, so it is present even on a response the chain aborts.
        response.setHeader(TRACE_ID_HEADER, traceId);

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
            write(method, path, response.getStatus(), elapsedMs(startedAt), null);
        } catch (IOException | ServletException | RuntimeException e) {
            // Reached only when something threw past the dispatcher — a failure inside
            // another filter, say — because @RestControllerAdvice has already handled
            // anything a controller threw. So this is not a duplicate of that log; it is
            // the only record these get.
            write(method, path, statusOrError(response), elapsedMs(startedAt), e);
            throw e;
        } finally {
            LogContext.clear();
        }
    }

    private void write(String method, String path, int status, long tookMs, Throwable failure) {
        LoggingEventBuilder event = log.atLevel(levelFor(status, path))
                // addKeyValue rather than the MDC: these two are numbers, and the MDC can
                // only hold strings. A duration stored as "43" is a keyword field in
                // Elasticsearch — it cannot be averaged, bucketed or asked for "> 500".
                .addKeyValue(LogContext.STATUS_CODE, status)
                .addKeyValue(LogContext.DURATION_MS, tookMs);
        if (failure != null) {
            event = event.setCause(failure);
        }
        event.log("{} {} -> {} ({} ms)", method, path, status, tookMs);
    }

    private static Level levelFor(int status, String path) {
        if (status >= 500) {
            return Level.ERROR;
        }
        if (isQuiet(path)) {
            return Level.DEBUG;
        }
        // Includes 401, which is ordinary for an expired token but is also what a
        // credential-stuffing run looks like in bulk. Worth being able to count.
        return status >= 400 ? Level.WARN : Level.INFO;
    }

    private static boolean isQuiet(String path) {
        return QUIET_PATHS.stream().anyMatch(path::startsWith);
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    /**
     * A response that was never committed still reports 200 after a throw, which would
     * record a failed request as a success.
     */
    private static int statusOrError(HttpServletResponse response) {
        int status = response.getStatus();
        return status >= 400 ? status : 500;
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String supplied = request.getHeader(TRACE_ID_HEADER);
        if (supplied != null && SAFE_TRACE_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * The initial dispatch only. An async request would otherwise be logged twice, once
     * when the handler returns the deferred result and once when it completes — two lines
     * for one request, with two different durations.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }
}
