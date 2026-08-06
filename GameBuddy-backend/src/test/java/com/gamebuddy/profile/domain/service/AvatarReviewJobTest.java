package com.gamebuddy.profile.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.profile.infrastructure.repository.AvatarReviewRepository;
import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.moderation.ImageAssessment;
import com.gamebuddy.shared.moderation.ImageModerationService;
import com.gamebuddy.shared.moderation.ModerationVerdict;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.ObjectNotFoundException;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvatarReviewJobTest {

    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
    private static final byte[] IMAGE = {1, 2, 3};

    private final AvatarReviewRepository queue = mock(AvatarReviewRepository.class);
    private final GamerRepository gamerRepository = mock(GamerRepository.class);
    private final ImageModerationService moderation = mock(ImageModerationService.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);

    private AvatarReviewJob job;

    @BeforeEach
    void setUp() {
        job = new AvatarReviewJob(queue, gamerRepository, moderation, storage, Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(job, "publishAfter", Duration.ofDays(3));
        when(storage.get(any(), any())).thenReturn(IMAGE);
    }

    private static Gamer pending(Double score, Instant uploadedAt) {
        Gamer g = new Gamer();
        g.setUserId("gamer-" + score + "-" + uploadedAt);
        g.setAvatarKey("avatars/x.jpg");
        g.setAvatarStatus(AvatarStatus.PENDING);
        g.setAvatarScore(score);
        g.setAvatarUploadedAt(uploadedAt);
        return g;
    }

    @Nested
    class Rescreening {

        @Test
        @DisplayName("an upload the classifier never saw is published once it scores clean")
        void publishesOnApprove() {
            Gamer gamer = pending(null, NOW.minus(Duration.ofMinutes(5)));
            when(queue.findUnscoredPending(any())).thenReturn(List.of(gamer));
            when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.APPROVE, 0.002));

            job.rescreenUnscored();

            assertEquals(AvatarStatus.APPROVED, gamer.getAvatarStatus());
            assertEquals(0.002, gamer.getAvatarScore());
            verify(storage)
                    .move(ObjectStorage.Bucket.UPLOADS, "avatars/x.jpg", ObjectStorage.Bucket.MEDIA, "avatars/x.jpg");
        }

        @Test
        void rejectsWhenTheSecondLookIsConfident() {
            Gamer gamer = pending(null, NOW);
            when(queue.findUnscoredPending(any())).thenReturn(List.of(gamer));
            when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.REJECT, 0.97));

            job.rescreenUnscored();

            assertEquals(AvatarStatus.REJECTED, gamer.getAvatarStatus());
            verify(storage, never()).move(any(), any(), any(), any());
        }

        @Test
        @DisplayName("the classifier still being down changes nothing, so the next run can try again")
        void leavesItAloneWhileTheClassifierIsStillDown() {
            Gamer gamer = pending(null, NOW);
            when(queue.findUnscoredPending(any())).thenReturn(List.of(gamer));
            when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.REVIEW, null));

            job.rescreenUnscored();

            assertEquals(AvatarStatus.PENDING, gamer.getAvatarStatus());
            assertNull(gamer.getAvatarScore(), "a null score is what marks it for the next attempt");
            verify(gamerRepository, never()).save(any());
        }

        @Test
        @DisplayName("a second look that is genuinely unsure records the score, which starts the deadline")
        void recordsAnAmbiguousScore() {
            Gamer gamer = pending(null, NOW);
            when(queue.findUnscoredPending(any())).thenReturn(List.of(gamer));
            when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.REVIEW, 0.44));

            job.rescreenUnscored();

            assertEquals(AvatarStatus.PENDING, gamer.getAvatarStatus());
            assertEquals(0.44, gamer.getAvatarScore(), "now ambiguous rather than merely unseen");
        }

        @Test
        @DisplayName("an upload whose bytes are gone is cleared rather than queued forever")
        void clearsAnUploadThatIsMissingFromStorage() {
            Gamer gamer = pending(null, NOW);
            when(queue.findUnscoredPending(any())).thenReturn(List.of(gamer));
            when(storage.get(any(), any())).thenThrow(new ObjectNotFoundException("avatars/x.jpg", null));

            job.rescreenUnscored();

            assertNull(gamer.getAvatarStatus());
            assertNull(gamer.getAvatarKey());
            verify(gamerRepository).save(gamer);
        }
    }

    @Nested
    class Publishing {

        @Test
        @DisplayName("an ambiguous upload nobody reviewed is published once the deadline passes")
        void publishesOverdue() {
            Gamer gamer = pending(0.42, NOW.minus(Duration.ofDays(4)));
            when(queue.findScoredPendingBefore(any(), any())).thenReturn(List.of(gamer));

            job.publishOverdue();

            assertEquals(AvatarStatus.APPROVED, gamer.getAvatarStatus());
            verify(storage)
                    .move(ObjectStorage.Bucket.UPLOADS, "avatars/x.jpg", ObjectStorage.Bucket.MEDIA, "avatars/x.jpg");
        }

        @Test
        @DisplayName("the deadline is measured from now, so the query is asked for the right cutoff")
        void asksForTheCorrectDeadline() {
            when(queue.findScoredPendingBefore(any(), any())).thenReturn(List.of());

            job.publishOverdue();

            verify(queue).findScoredPendingBefore(eq(NOW.minus(Duration.ofDays(3))), any());
        }
    }
}
