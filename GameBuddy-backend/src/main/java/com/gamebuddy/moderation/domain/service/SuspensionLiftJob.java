package com.gamebuddy.moderation.domain.service;

import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends suspensions when their time is up, so a time-limited block does not need anyone to
 * remember to lift it.
 *
 * <p>A suspension is {@code is_blocked = true} with a {@code suspended_until} time; nothing
 * happens at that instant on its own. Login lifts an expired one the moment the person tries
 * to sign in, but somebody who never comes back would otherwise stay blocked forever, and
 * the console would show them as blocked long after their week was served. This sweeps for
 * them hourly.
 *
 * <p>Hourly is ample: a suspension measured in hours or days does not need minute precision,
 * and the person's own next sign-in is the path that actually matters to them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspensionLiftJob {

    private final GamerRepository gamerRepository;
    private final Clock clock;

    @Scheduled(cron = "${gamebuddy.moderation.suspension-lift-cron:0 15 * * * *}")
    @Transactional
    public void liftExpired() {
        List<Gamer> expired = gamerRepository.findExpiredSuspensions(clock.instant());
        if (expired.isEmpty()) {
            return;
        }
        for (Gamer gamer : expired) {
            gamer.setIsBlocked(false);
            gamer.setSuspendedUntil(null);
        }
        gamerRepository.saveAll(expired);
        log.info("Lifted {} expired suspension(s)", expired.size());
    }
}
