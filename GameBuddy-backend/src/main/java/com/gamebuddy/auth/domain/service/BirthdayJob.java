package com.gamebuddy.auth.domain.service;

import com.gamebuddy.shared.repository.GamerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the derived age column true to the date of birth it came from.
 *
 * <p>{@code Gamer.age} is a cache: the recommendation feed filters on it in native SQL and
 * the model reads it as a feature, and neither can compute a date difference cheaply per
 * row. A cache that is never refreshed is a lie with a timestamp, and this one drifts by
 * exactly one year per person per year — visible on their own profile, which is where a
 * wrong age is most annoying.
 *
 * <p>Nothing about eligibility depends on this running. Anyone below 18 was refused at the
 * point they gave their date of birth, and nobody ages downwards; if this job never ran,
 * displayed ages would go stale and the safety floor would still hold.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BirthdayJob {

    private final GamerRepository gamerRepository;

    @Transactional
    @Scheduled(cron = "${gamebuddy.age.refresh-cron:0 15 3 * * *}")
    public void refreshAges() {
        try {
            int updated = gamerRepository.refreshAgesFromBirthDate();
            if (updated > 0) {
                log.info("Refreshed the age on {} account(s) that had a birthday", updated);
            }
        } catch (RuntimeException e) {
            // Never let a scheduled failure take the scheduler thread down with it.
            log.error("Refreshing ages from dates of birth failed", e);
        }
    }
}
