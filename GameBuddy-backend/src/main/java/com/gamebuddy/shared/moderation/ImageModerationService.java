package com.gamebuddy.shared.moderation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Screening, with a failure policy.
 *
 * <p>The client is one HTTP call; this exists for what happens when that call does not
 * come back. The tempting answer — approve, so uploads keep working while the classifier
 * is down — is the wrong one: it makes an outage a window during which anything can be
 * published, and outages are exactly when nobody is watching. So this <strong>fails
 * closed</strong> to {@link ModerationVerdict#REVIEW}.
 *
 * <p>Closed to REVIEW rather than to REJECT, because a classifier being unreachable is not
 * evidence about the picture. Rejecting would tell an innocent user their photograph was
 * refused as sexual content, which is both untrue and unappealable. REVIEW holds the image
 * unpublished until a person looks, which is the honest outcome: the queue grows during an
 * outage and drains afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageModerationService {

    private final ImageModerationClient client;

    /**
     * @param image the normalised bytes — what will actually be stored, not what was
     *     uploaded. Screening the original would let a client send a clean image that
     *     decodes to something else, and would waste the work on bytes we are discarding.
     */
    public ImageAssessment screen(byte[] image, String filename) {
        try {
            Resource part = new ByteArrayResource(image) {
                @Override
                public String getFilename() {
                    // The multipart part needs a filename or the model's parser rejects
                    // the request as malformed — a 422 on a perfectly well-formed upload.
                    return filename;
                }
            };

            ImageModerationClient.ModerationResponse response = client.moderate(part);
            ModerationVerdict verdict = parse(response.verdict());
            log.info("Image screened: {} ({})", verdict, response.score());
            return new ImageAssessment(verdict, response.score());
        } catch (RuntimeException e) {
            // The image itself is never logged. Whatever this just handled, the logs are
            // the last place it should end up.
            log.error("Image screening failed, holding for review: {}", e.getMessage());
            return ImageAssessment.held();
        }
    }

    private ModerationVerdict parse(String verdict) {
        try {
            return ModerationVerdict.valueOf(verdict);
        } catch (IllegalArgumentException | NullPointerException e) {
            // An unrecognised verdict means the two sides have drifted. Holding for a
            // human is the only safe reading of an answer we do not understand.
            log.error("Unrecognised moderation verdict '{}', holding for review", verdict);
            return ModerationVerdict.REVIEW;
        }
    }
}
