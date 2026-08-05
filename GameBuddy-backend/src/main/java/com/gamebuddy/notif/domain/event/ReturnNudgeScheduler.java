package com.gamebuddy.notif.domain.event;

import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invites people back who have stopped opening the app.
 *
 * <p>The one notification nobody asked for, so it is the one with rules. Every other push
 * this app sends is a response to something another person did — a message, a like, a
 * match — and those are welcome by construction. A reminder is the app talking about
 * itself.
 *
 * <p><b>At most two per absence, and never within a week of each other.</b> The count
 * resets when they come back, so the cap is on this absence rather than on their lifetime:
 * somebody who returns and drifts away again next year is worth reminding again. Somebody
 * who is ignoring the app is not worth reminding a third time — after two unanswered
 * nudges the honest conclusion is that they do not want it, and continuing is how an app
 * gets its notifications switched off for good, which costs every future message too.
 *
 * <p><b>It says what they are missing, not that they are missed.</b> "3 people liked you
 * back" is a fact about their account that they would want to know. "We miss you!" is the
 * app performing an emotion it does not have, and it is the line that makes people
 * uninstall. When there is nothing concrete waiting, this sends nothing at all — a nudge
 * with no reason behind it is the definition of spam.
 *
 * <p>Off entirely for anyone who set {@code remindersEnabled} to false, which the settings
 * screen exposes in one tap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnNudgeScheduler {

    /** Idle this long and the first nudge is due. */
    private static final Duration FIRST_NUDGE_AFTER = Duration.ofDays(3);

    /** Idle this long and the second — and last — is due. */
    private static final Duration SECOND_NUDGE_AFTER = Duration.ofDays(14);

    /** Never two nudges closer together than this, whatever the thresholds say. */
    private static final Duration MIN_GAP = Duration.ofDays(7);

    /** After this many in one absence, stop. They have heard us. */
    private static final int MAX_NUDGES_PER_ABSENCE = 2;

    /**
     * How many to nudge in one run.
     *
     * <p>A bound, not a target. It stops a first run over a large dormant population from
     * queueing hundreds of thousands of rows in one transaction; the rest are picked up on
     * the next run, and a day's delay on a reminder about a three-day absence is nothing.
     */
    private static final int BATCH_SIZE = 500;

    private final GamerRepository gamerRepository;
    private final ApplicationEventPublisher events;
    private final ReturnNudgeCopy copy;
    private final Clock clock;

    @Value("${notifications.nudges.enabled:true}")
    private boolean enabled;

    /**
     * Runs once a day, mid-morning UTC.
     *
     * <p>Not hourly. A reminder is not urgent, and a daily pass means somebody who has been
     * away three days hears once rather than being reconsidered every hour until the clock
     * happens to line up.
     */
    @Scheduled(cron = "${notifications.nudges.cron:0 0 10 * * *}")
    @Transactional
    public void nudgeDormantGamers() {
        if (!enabled) {
            return;
        }

        Instant now = clock.instant();
        List<Gamer> dormant = gamerRepository.findNudgeable(
                now.minus(FIRST_NUDGE_AFTER), now.minus(MIN_GAP), MAX_NUDGES_PER_ABSENCE, BATCH_SIZE);

        int sent = 0;
        for (Gamer gamer : dormant) {
            if (!isDue(gamer, now)) {
                continue;
            }
            String body = copy.bodyFor(gamer);
            if (body == null) {
                // Nothing is actually waiting for them. Saying so anyway would be an
                // advert, and it would teach them that these are not worth opening.
                continue;
            }

            events.publishEvent(new NotificationRequestedEvent(
                    gamer.getFcmToken(), copy.titleFor(gamer), body, NotificationKind.RETURN));

            gamer.setLastNudgedAt(now);
            gamer.setNudgeCount(gamer.getNudgeCount() + 1);
            gamerRepository.save(gamer);
            sent++;
        }

        if (sent > 0) {
            log.info("Queued {} return nudge(s) out of {} dormant gamer(s)", sent, dormant.size());
        }
    }

    /**
     * Whether this gamer is at one of the two thresholds.
     *
     * <p>The query already applied the cheap parts — idle at all, under the cap, outside
     * the minimum gap — because they filter the population down. Which of the two
     * thresholds applies depends on how many have already been sent, and that is clearer
     * here than as another clause in the SQL.
     */
    private boolean isDue(Gamer gamer, Instant now) {
        Instant idleSince = gamer.getLastActiveAt();
        if (idleSince == null) {
            return false;
        }
        Duration idle = Duration.between(idleSince, now);
        return switch (gamer.getNudgeCount()) {
            case 0 -> idle.compareTo(FIRST_NUDGE_AFTER) >= 0;
            case 1 -> idle.compareTo(SECOND_NUDGE_AFTER) >= 0;
            default -> false;
        };
    }
}
