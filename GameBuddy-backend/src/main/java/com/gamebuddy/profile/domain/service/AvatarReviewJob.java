package com.gamebuddy.profile.domain.service;

import com.gamebuddy.profile.infrastructure.repository.AvatarReviewRepository;
import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.moderation.ImageAssessment;
import com.gamebuddy.shared.moderation.ImageModerationService;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.ObjectNotFoundException;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the avatar queue so a human never has to.
 *
 * <p>The queue exists because the classifier has three answers and the middle one needs a
 * person. What it must not become is a job: this is a solo-operated product, and a queue
 * that has to be watched is one that will not be — at which point an ordinary user's
 * photograph is invisible indefinitely, having been promised a review by nobody. That
 * outcome is both far more likely and, at these thresholds, far more harmful than the rare
 * borderline image going live where it can be reported.
 *
 * <p>Two passes, for the two genuinely different reasons an upload sits at PENDING:
 *
 * <ol>
 *   <li><strong>Never scored.</strong> {@code avatarScore} is null, so the classifier was
 *       unreachable when the image arrived and nothing has judged it. This is the common
 *       case in practice — a restart or an out-of-memory on the model container queues
 *       every upload in the window — and it wants another attempt, not a moderator.
 *   <li><strong>Scored and ambiguous.</strong> The classifier looked and was unsure.
 *       Re-screening is pointless: the same bytes give the same answer. These are held for
 *       the deadline and then published, still fully reportable.
 * </ol>
 *
 * <p>Measured before this was written: ordinary images score around 0.001 against an
 * approve threshold of 0.20, so the second pass is expected to be rare and the first is
 * the one that matters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarReviewJob {

    /** How many uploads one run will handle. Bounds the work after a long outage. */
    private static final int BATCH = 200;

    private final AvatarReviewRepository queue;
    private final GamerRepository gamerRepository;
    private final ImageModerationService moderation;
    private final ObjectStorage storage;
    private final Clock clock;

    /**
     * How long a genuinely ambiguous image waits for a person before it is published.
     *
     * <p>Not a safety threshold — anything the classifier was confident about was already
     * rejected outright, and everything here remains reportable and bannable. It is a
     * promise about how long someone's photograph can be held without an answer.
     */
    @Value("${gamebuddy.avatars.publish-after:P3D}")
    private Duration publishAfter;

    /**
     * Retries the ones the classifier never saw.
     *
     * <p>Every ten minutes rather than hourly: this is the recovery path from a model
     * outage, and the gap between the service coming back and the queue draining is time a
     * user spends looking at their own greyed-out picture.
     */
    @Scheduled(cron = "${gamebuddy.avatars.rescreen-cron:0 */10 * * * *}")
    @Transactional
    public void rescreenUnscored() {
        List<Gamer> unscored = queue.findUnscoredPending(PageRequest.of(0, BATCH));
        if (unscored.isEmpty()) {
            return;
        }

        int decided = 0;
        for (Gamer gamer : unscored) {
            byte[] image;
            try {
                image = storage.get(ObjectStorage.Bucket.UPLOADS, gamer.getAvatarKey());
            } catch (ObjectNotFoundException e) {
                // The object is gone but the row still points at it. Nothing can ever be
                // decided about this upload, so leaving it PENDING would keep it in the
                // queue forever; clearing it puts the gamer back on their monogram.
                log.warn("Pending avatar for {} is missing from storage; clearing it", gamer.getUserId());
                gamer.setAvatarKey(null);
                gamer.setAvatarStatus(null);
                gamerRepository.save(gamer);
                continue;
            }

            ImageAssessment assessment = moderation.screen(image, "avatar.jpg");
            if (assessment.unscored()) {
                // Still down. Leave it exactly as it was and try again next run — the
                // whole point of this pass is that it costs nothing to repeat.
                continue;
            }

            apply(gamer, assessment);
            decided++;
        }

        log.info("Re-screened {} pending avatar(s), {} decided", unscored.size(), decided);
    }

    /**
     * Publishes anything a person has not got to in time.
     *
     * <p>Hourly is ample for a deadline measured in days, and it means the work is spread
     * rather than arriving as one burst at midnight.
     */
    @Scheduled(cron = "${gamebuddy.avatars.publish-cron:0 20 * * * *}")
    @Transactional
    public void publishOverdue() {
        Instant deadline = clock.instant().minus(publishAfter);
        List<Gamer> overdue = queue.findScoredPendingBefore(deadline, PageRequest.of(0, BATCH));

        for (Gamer gamer : overdue) {
            storage.move(
                    ObjectStorage.Bucket.UPLOADS,
                    gamer.getAvatarKey(),
                    ObjectStorage.Bucket.MEDIA,
                    gamer.getAvatarKey());
            gamer.setAvatarStatus(AvatarStatus.APPROVED);
            gamerRepository.save(gamer);

            // At INFO with the score, deliberately. This is the application overruling a
            // held decision on nobody's authority but a clock's, and it should be visible
            // in the log afterwards — including for retuning the thresholds if these turn
            // out to cluster near the top of the band.
            log.info(
                    "Published avatar for {} unreviewed after {} (score {})",
                    gamer.getUserId(),
                    publishAfter,
                    gamer.getAvatarScore());
        }
    }

    /** Applies a fresh verdict to an upload that was already stored. */
    private void apply(Gamer gamer, ImageAssessment assessment) {
        gamer.setAvatarScore(assessment.score());

        switch (assessment.verdict()) {
            case APPROVE -> {
                storage.move(
                        ObjectStorage.Bucket.UPLOADS,
                        gamer.getAvatarKey(),
                        ObjectStorage.Bucket.MEDIA,
                        gamer.getAvatarKey());
                gamer.setAvatarStatus(AvatarStatus.APPROVED);
            }
            case REJECT -> gamer.setAvatarStatus(AvatarStatus.REJECTED);
            // Now genuinely ambiguous rather than merely unseen. It stays in the queue and
            // the publish deadline starts to matter to it.
            case REVIEW -> gamer.setAvatarStatus(AvatarStatus.PENDING);
        }
        gamerRepository.save(gamer);
    }
}
