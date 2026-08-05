package com.gamebuddy.notif.domain.event;

import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Remembers when each gamer was last seen, so the app knows who has drifted away.
 *
 * <p>Nothing recorded this before. "Last modified" tracks writes, and a session row tracks
 * logins — somebody who opens the app daily for a month without changing anything looks
 * identical to somebody who left after signing up. Re-engagement needs the difference.
 *
 * <p><b>Written at most once an hour per gamer.</b> A write on every request would put an
 * UPDATE on the hot path of the busiest endpoints in the app to record something only ever
 * read in days. The staleness that buys is an hour, against thresholds measured in days.
 *
 * <p>Registered from {@code ApplicationConfig} rather than annotated {@code @Component}.
 * A component-scanned filter is pulled into every {@code @WebMvcTest} slice, and this one
 * needs a repository that those slices deliberately do not have — so scanning it turned
 * every controller test into a context-load failure.
 *
 * <p>In its own transaction, and failures are swallowed. This is bookkeeping attached to
 * somebody else's request: it must not roll back the swipe that triggered it, and it must
 * not turn a database hiccup into a failed request for a gamer who was only reading their
 * inbox.
 */
@Slf4j
@RequiredArgsConstructor
public class LastActiveTracker extends OncePerRequestFilter {

    /** How stale the stored value may get before it is worth a write. */
    private static final Duration WRITE_EVERY = Duration.ofHours(1);

    private final GamerRepository gamerRepository;
    private final Clock clock;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Downstream first. Recording that somebody was here is worth nothing if it
        // delays what they came for, and a failure here must not stop the response.
        chain.doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Gamer gamer)) {
            return;
        }

        Instant now = clock.instant();
        Instant last = gamer.getLastActiveAt();
        boolean stale = last == null || Duration.between(last, now).compareTo(WRITE_EVERY) >= 0;

        // Also write when a nudge is outstanding, however recent the timestamp: opening
        // the app is what ends an absence, and leaving the count set would keep them
        // eligible for the second nudge they have just made unnecessary.
        if (!stale && gamer.getNudgeCount() == 0) {
            return;
        }

        touch(gamer.getUserId(), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void touch(String userId, Instant now) {
        try {
            gamerRepository.touchLastActive(userId, now);
        } catch (RuntimeException e) {
            // Deliberately not rethrown. The request has already been answered, and losing
            // one activity timestamp is worth less than a 500 on a read.
            log.debug("Could not record activity for {}", userId, e);
        }
    }
}
