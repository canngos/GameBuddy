package com.gamebuddy.profile.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.interfaces.dto.AvatarUploadResponseBody;
import com.gamebuddy.profile.interfaces.response.AvatarUploadResponse;
import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.moderation.ImageAssessment;
import com.gamebuddy.shared.moderation.ImageModerationService;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.ImageNormaliser;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Accepting an uploaded avatar.
 *
 * <p>The order of operations is the whole design, and it is chosen so that no arrangement
 * of failures publishes an unscreened image:
 *
 * <ol>
 *   <li><strong>Normalise first.</strong> The bytes that get screened are the bytes that
 *       get stored — see {@link ImageNormaliser}. Screening the upload and storing the
 *       original would let a file that decodes one way for the classifier decode another
 *       way for a phone.
 *   <li><strong>Write to the private bucket.</strong> Nothing is publicly reachable at
 *       this point, whatever happens next.
 *   <li><strong>Screen.</strong> Unreachable classifier means PENDING, never APPROVED —
 *       see {@link ImageModerationService}.
 *   <li><strong>Promote only on APPROVE.</strong> The move to the public bucket is the
 *       last step and the only one that makes the image visible.
 * </ol>
 *
 * <p>A crash anywhere leaves the image in the private bucket, which is the safe end state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarUploadService {

    private final GamerRepository gamerRepository;
    private final ObjectStorage storage;
    private final ImageNormaliser normaliser;
    private final ImageModerationService moderation;
    private final Clock clock;

    @Transactional
    public AvatarUploadResponse upload(Gamer principal, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "No image was uploaded");
        }
        byte[] uploaded;
        try {
            uploaded = file.getBytes();
        } catch (IOException e) {
            // The caller is told only that the file was unreadable. Which is true, but it
            // is also what a full temp directory looks like from the outside, so the real
            // cause has to be recorded somewhere.
            log.warn("Could not read the uploaded avatar for {}", principal.getUserId(), e);
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "The upload could not be read");
        }
        return store(principal, uploaded);
    }

    private AvatarUploadResponse store(Gamer principal, byte[] uploaded) {
        Gamer gamer = gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        byte[] image;
        try {
            image = normaliser.normalise(uploaded);
        } catch (ImageNormaliser.UnreadableImageException e) {
            // A 400 about the file, not a moderation verdict. Telling someone their
            // photograph was refused as sexual content when it was actually a corrupt
            // upload is a bug with a reputational cost.
            //
            // DEBUG, not WARN: a phone sending a format the server cannot decode is a
            // client bug worth being able to find, but it is the user's problem and not
            // the server's, and at WARN one broken client build would drown the log.
            log.debug("Unreadable avatar upload from {}: {}", gamer.getUserId(), e.getMessage());
            throw new BusinessException(TransactionCode.INVALID_REQUEST, e.getMessage());
        }

        // A fresh key every time rather than one per gamer. Overwriting would mean the
        // old image is served from caches under a key that now points at a new one, and
        // it would leave no way to tell a moderator which bytes were actually judged.
        String key = "avatars/%s/%s.%s".formatted(gamer.getUserId(), UUID.randomUUID(), ImageNormaliser.EXTENSION);

        storage.put(ObjectStorage.Bucket.UPLOADS, key, image, ImageNormaliser.CONTENT_TYPE);

        ImageAssessment assessment = moderation.screen(image, "avatar." + ImageNormaliser.EXTENSION);

        String previousKey = gamer.getAvatarKey();
        AvatarStatus previousStatus = gamer.getAvatarStatus();

        // Recorded whatever the verdict, including null when the classifier never
        // answered — that null is what tells AvatarReviewJob this one is worth another
        // try rather than a person's attention.
        gamer.setAvatarScore(assessment.score());
        gamer.setAvatarUploadedAt(clock.instant());

        switch (assessment.verdict()) {
            case APPROVE -> {
                storage.move(ObjectStorage.Bucket.UPLOADS, key, ObjectStorage.Bucket.MEDIA, key);
                gamer.setAvatarKey(key);
                gamer.setAvatarStatus(AvatarStatus.APPROVED);
            }
            case REVIEW -> {
                gamer.setAvatarKey(key);
                gamer.setAvatarStatus(AvatarStatus.PENDING);
            }
            case REJECT -> {
                // The key is still recorded. A rejected image that is immediately
                // forgotten cannot be appealed, and an account that uploads the same
                // thing repeatedly looks like a first offence every time.
                gamer.setAvatarKey(key);
                gamer.setAvatarStatus(AvatarStatus.REJECTED);
            }
        }

        gamerRepository.save(gamer);
        cleanUpPrevious(previousKey, previousStatus, key);

        log.info("Avatar upload for {}: {}", gamer.getUserId(), gamer.getAvatarStatus());

        AvatarUploadResponseBody body = new AvatarUploadResponseBody();
        body.setStatus(gamer.getAvatarStatus().name());
        body.setUrl(storage.publicUrl(key));

        AvatarUploadResponse response = new AvatarUploadResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /**
     * Removes the image this one replaced.
     *
     * <p>Storage is 10GB on the free tier and an avatar is a few tens of kilobytes, so
     * this is not about space — it is that an image nobody can reach through the
     * application should not still exist in a public bucket, findable by anyone who
     * kept the URL.
     */
    private void cleanUpPrevious(String previousKey, AvatarStatus previousStatus, String newKey) {
        if (previousKey == null || previousKey.equals(newKey)) {
            return;
        }
        ObjectStorage.Bucket bucket =
                previousStatus == AvatarStatus.APPROVED ? ObjectStorage.Bucket.MEDIA : ObjectStorage.Bucket.UPLOADS;
        storage.delete(bucket, previousKey);
    }
}
