package com.gamebuddy.profile.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.profile.infrastructure.repository.AvatarReviewRepository;
import com.gamebuddy.profile.interfaces.dto.AvatarImageResponseBody;
import com.gamebuddy.profile.interfaces.dto.PendingAvatarDto;
import com.gamebuddy.profile.interfaces.dto.PendingAvatarsResponseBody;
import com.gamebuddy.profile.interfaces.response.AvatarImageResponse;
import com.gamebuddy.profile.interfaces.response.PendingAvatarsResponse;
import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The human half of avatar moderation.
 *
 * <p>The classifier has three verdicts and the middle one, REVIEW, exists precisely because
 * an automated call should not be final on a person's photograph. Until now there was
 * nothing behind it: {@code AvatarUploadService} set {@link AvatarStatus#PENDING}, left the
 * image in the private bucket, and no endpoint listed or resolved those uploads. A picture
 * the model was unsure about was therefore refused forever, silently, with the owner told
 * it was "under review" by nobody.
 *
 * <p>Approving is the same promotion the upload path performs on APPROVE — copy into the
 * public bucket, then flip the status — so an image becomes visible through exactly one
 * code path however it got there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarReviewService {

    /**
     * How many pending uploads one page returns.
     *
     * <p>Bounded because the queue is unbounded: a classifier outage marks everything
     * PENDING, and the screen that a moderator opens to deal with that must not be the
     * screen that tries to load ten thousand rows.
     */
    private static final int QUEUE_LIMIT = 100;

    /** Uploads are normalised to JPEG before they are ever stored; see ImageNormaliser. */
    private static final String DATA_URI_PREFIX = "data:image/jpeg;base64,";

    private final AvatarReviewRepository queue;
    private final GamerRepository gamerRepository;
    private final ObjectStorage storage;

    @Transactional(readOnly = true)
    public PendingAvatarsResponse pending() {
        List<PendingAvatarDto> waiting = queue
                .findByAvatarStatusAndDeletedAtIsNullOrderByLastModifiedDateAsc(
                        AvatarStatus.PENDING, PageRequest.of(0, QUEUE_LIMIT))
                .stream()
                .map(gamer -> new PendingAvatarDto(
                        gamer.getUserId(),
                        gamer.getGamerUsername(),
                        // The upload's own clock, not lastModifiedDate: that is an
                        // @UpdateTimestamp, so a gamer editing their username would make a
                        // two-day-old upload look like it had just arrived.
                        gamer.getAvatarUploadedAt(),
                        gamer.getAvatarScore()))
                .toList();

        PendingAvatarsResponse response = new PendingAvatarsResponse();
        response.setBody(new BaseBody<>(new PendingAvatarsResponseBody(waiting)));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /**
     * The image under review, inlined as a {@code data:} URI.
     *
     * <p>Keyed by gamer, never by object key. The console can therefore only ever ask for
     * the image currently pending on an account — there is no parameter that could be made
     * to name some other object in the private bucket, which is the mistake that would
     * turn a moderation tool into a way to read anything ever uploaded.
     *
     * <p>Encoded here rather than streamed as {@code image/jpeg}: see
     * {@link AvatarImageResponseBody} for why the raw form could not be authenticated from
     * the client.
     */
    @Transactional(readOnly = true)
    public AvatarImageResponse imageUnderReview(String userId) {
        Gamer gamer = requirePending(userId);
        byte[] image = storage.get(ObjectStorage.Bucket.UPLOADS, gamer.getAvatarKey());

        AvatarImageResponse response = new AvatarImageResponse();
        response.setBody(new BaseBody<>(new AvatarImageResponseBody(
                DATA_URI_PREFIX + Base64.getEncoder().encodeToString(image))));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Transactional
    public DefaultMessageResponse approve(String userId) {
        Gamer gamer = requirePending(userId);

        storage.move(
                ObjectStorage.Bucket.UPLOADS, gamer.getAvatarKey(), ObjectStorage.Bucket.MEDIA, gamer.getAvatarKey());
        gamer.setAvatarStatus(AvatarStatus.APPROVED);
        gamerRepository.save(gamer);

        log.info("Avatar approved for {}", userId);
        return DefaultMessageResponse.of("Avatar approved");
    }

    @Transactional
    public DefaultMessageResponse reject(String userId) {
        Gamer gamer = requirePending(userId);

        // The key stays, and the image stays in the private bucket. Same reasoning as the
        // upload path's REJECT branch: an image that is immediately forgotten cannot be
        // appealed, and somebody re-uploading the same picture looks like a first offence
        // every time.
        gamer.setAvatarStatus(AvatarStatus.REJECTED);
        gamerRepository.save(gamer);

        log.info("Avatar rejected for {}", userId);
        return DefaultMessageResponse.of("Avatar rejected");
    }

    /**
     * Refuses to act on anything that is not currently awaiting review.
     *
     * <p>Guards the race that a queue makes likely rather than rare: the gamer can upload
     * again while their previous attempt is on a moderator's screen, and the stale tap that
     * follows would otherwise publish an image nobody looked at.
     */
    private Gamer requirePending(String userId) {
        Gamer gamer = requireGamer(userId);
        if (gamer.getAvatarStatus() != AvatarStatus.PENDING || gamer.getAvatarKey() == null) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "no avatar is awaiting review");
        }
        return gamer;
    }

    private Gamer requireGamer(String userId) {
        return gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }
}
