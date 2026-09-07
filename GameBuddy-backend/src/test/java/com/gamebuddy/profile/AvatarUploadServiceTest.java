package com.gamebuddy.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.domain.service.AvatarUploadService;
import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.moderation.ImageAssessment;
import com.gamebuddy.shared.moderation.ImageModerationService;
import com.gamebuddy.shared.moderation.ModerationVerdict;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.ImageNormaliser;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * The upload path, with the classifier and the bucket stubbed.
 *
 * <p>What is worth asserting here is not that a file gets written — it is the ordering.
 * Every test below is a way the feature could publish an image nobody screened, which is
 * the one failure this whole change exists to prevent.
 */
@ExtendWith(MockitoExtension.class)
class AvatarUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

    @Mock
    GamerRepository gamerRepository;

    @Mock
    ObjectStorage storage;

    @Mock
    ImageModerationService moderation;

    AvatarUploadService service;
    Gamer gamer;

    @BeforeEach
    void setUp() {
        service = new AvatarUploadService(
                gamerRepository, storage, new ImageNormaliser(), moderation, Clock.fixed(NOW, ZoneOffset.UTC));
        gamer = new Gamer();
        gamer.setUserId("gamer-1");
    }

    private static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "photo.png", "image/png", png());
    }

    private void gamerExists() {
        when(gamerRepository.findById("gamer-1")).thenReturn(Optional.of(gamer));
    }

    @Test
    void anApprovedImageIsPromotedToThePublicBucket() {
        gamerExists();
        when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.APPROVE, 0.01));

        service.upload(gamer, upload());

        verify(storage).move(eq(ObjectStorage.Bucket.UPLOADS), any(), eq(ObjectStorage.Bucket.MEDIA), any());
        assertThat(gamer.getAvatarStatus()).isEqualTo(AvatarStatus.APPROVED);
    }

    @Test
    void anUncertainImageStaysInThePrivateBucket() {
        gamerExists();
        when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.REVIEW, 0.45));

        service.upload(gamer, upload());

        verify(storage, never()).move(any(), any(), any(), any());
        assertThat(gamer.getAvatarStatus()).isEqualTo(AvatarStatus.PENDING);
        assertThat(gamer.hasVisibleAvatar()).isFalse();
    }

    @Test
    void aRejectedImageIsNeverPromoted() {
        gamerExists();
        when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.REJECT, 0.95));

        service.upload(gamer, upload());

        verify(storage, never()).move(any(), any(), any(), any());
        assertThat(gamer.getAvatarStatus()).isEqualTo(AvatarStatus.REJECTED);
        assertThat(gamer.hasVisibleAvatar()).isFalse();
    }

    @Test
    void theBytesScreenedAreTheBytesStored() {
        gamerExists();
        when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.APPROVE, 0.01));

        service.upload(gamer, upload());

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> screened = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(any(), any(), stored.capture(), any());
        verify(moderation).screen(screened.capture(), any());

        // Not merely equal — the same normalised array. Screening the upload while storing
        // something else would let a file that decodes one way for the classifier decode
        // another way for a phone.
        assertThat(stored.getValue()).isSameAs(screened.getValue());
    }

    @Test
    void theStoredImageIsReEncodedNotThePayloadThatWasSent() {
        gamerExists();
        when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.APPROVE, 0.01));
        byte[] sent = png();

        service.upload(gamer, new MockMultipartFile("file", "photo.png", "image/png", sent));

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(any(), any(), stored.capture(), any());
        // A PNG went in; a JPEG comes out. That round trip is what drops EXIF — including
        // the GPS coordinates a phone photograph carries — and anything appended after
        // the image data.
        assertThat(stored.getValue()).isNotEqualTo(sent);
        assertThat(stored.getValue()[0]).isEqualTo((byte) 0xFF);
        assertThat(stored.getValue()[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void theImageIsWrittenPrivatelyBeforeItIsScreened() {
        gamerExists();
        when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.APPROVE, 0.01));

        service.upload(gamer, upload());

        var order = org.mockito.Mockito.inOrder(storage, moderation);
        order.verify(storage).put(eq(ObjectStorage.Bucket.UPLOADS), any(), any(), any());
        order.verify(moderation).screen(any(), any());
        order.verify(storage).move(any(), any(), any(), any());
    }

    @Test
    void somethingThatIsNotAnImageIsRefusedBeforeAnythingIsStored() {
        gamerExists();

        assertThatThrownBy(() ->
                        service.upload(gamer, new MockMultipartFile("file", "x.png", "image/png", "nope".getBytes())))
                .isInstanceOf(BusinessException.class);

        verify(storage, never()).put(any(), any(), any(), any());
        verify(moderation, never()).screen(any(), any());
    }

    @Test
    void anEmptyUploadIsRefused() {
        assertThatThrownBy(() -> service.upload(gamer, new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void replacingAnApprovedAvatarRemovesTheOldOneFromThePublicBucket() {
        gamer.setAvatarKey("avatars/gamer-1/old.jpg");
        gamer.setAvatarStatus(AvatarStatus.APPROVED);
        gamerExists();
        when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.APPROVE, 0.01));

        service.upload(gamer, upload());

        // An image nobody can reach through the application should not still be sitting in
        // a public bucket for anyone who kept the URL.
        verify(storage).delete(ObjectStorage.Bucket.MEDIA, "avatars/gamer-1/old.jpg");
    }

    @Test
    void replacingAPendingAvatarRemovesItFromThePrivateBucket() {
        gamer.setAvatarKey("avatars/gamer-1/old.jpg");
        gamer.setAvatarStatus(AvatarStatus.PENDING);
        gamerExists();
        when(moderation.screen(any(), any())).thenReturn(new ImageAssessment(ModerationVerdict.REVIEW, 0.45));

        service.upload(gamer, upload());

        verify(storage).delete(ObjectStorage.Bucket.UPLOADS, "avatars/gamer-1/old.jpg");
    }
}
