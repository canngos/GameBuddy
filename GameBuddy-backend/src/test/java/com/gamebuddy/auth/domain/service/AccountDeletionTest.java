package com.gamebuddy.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.auth.infrastructure.repository.*;
import com.gamebuddy.auth.interfaces.request.DeleteAccountRequest;
import com.gamebuddy.auth.interfaces.request.FcmTokenRequest;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.common.security.JwtService;
import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.CosmeticKind;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.Games;
import com.gamebuddy.shared.entity.Keywords;
import com.gamebuddy.shared.event.AccountDeletedEvent;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountDeletionTest {

    @InjectMocks
    private DefaultAuthService authService;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private VerificationCodeRepository verificationCodeRepository;

    @Mock
    private GamesRepository gamesRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private KeywordsRepository keywordsRepository;

    @Mock
    private AvatarsRepository avatarsRepository;

    @Mock
    private GamerCosmeticRepository gamerCosmeticRepository;

    @Mock
    private GamerBadgeRepository gamerBadgeRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender emailSender;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private ObjectStorage objectStorage;

    @Spy
    private AuthRateLimiters rateLimiters = new AuthRateLimiters(
            new RateLimiter(10, Duration.ofMinutes(15)),
            new RateLimiter(3, Duration.ofMinutes(15)),
            new RateLimiter(10, Duration.ofMinutes(15)));

    private Gamer gamer;

    @BeforeEach
    void setUp() {
        gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setEmail("me@example.com");
        gamer.setGamerUsername("me");
        gamer.setPwd("encoded");
        gamer.setRole(Role.USER);
        gamer.setAge(25);
        gamer.setCountry("TR");
        gamer.setGender("M");
        gamer.setAvatar(UUID.randomUUID());
        gamer.setFcmToken("device-token");
        gamer.setIsVerified(true);
        gamer.setIsRegistered(true);
        gamer.getKeywords().add(new Keywords());
        gamer.getLikedgames().add(new Games());

        Cosmetic frame = new Cosmetic();
        frame.setId(UUID.randomUUID());
        frame.setKind(CosmeticKind.FRAME);
        gamer.setEquippedFrame(frame);

        when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
        when(passwordEncoder.matches("correct", "encoded")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("re-encoded");
    }

    private static DeleteAccountRequest request(String password) {
        DeleteAccountRequest r = new DeleteAccountRequest();
        r.setCurrentPassword(password);
        return r;
    }

    @Nested
    class DeleteAccount {

        @Test
        @DisplayName("deletion tells the other modules, so their personal data goes too")
        void testDeleteAccount_publishesAccountDeleted() {
            authService.deleteAccount(gamer, request("correct"));

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(events, atLeastOnce()).publishEvent(captor.capture());

            // The impression log lives in the match module and holds this user id in both
            // directions. Anonymising the gamer row is not enough while another table
            // still names them, and auth must not reach across the boundary to clear it.
            AccountDeletedEvent event = captor.getAllValues().stream()
                    .filter(AccountDeletedEvent.class::isInstance)
                    .map(AccountDeletedEvent.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no AccountDeletedEvent was published"));
            assertEquals(gamer.getUserId(), event.userId());
        }

        @Test
        @DisplayName("a stolen token alone cannot destroy the account")
        void testDeleteAccount_whenPasswordWrong_ReturnErrorCode150() {
            DeleteAccountRequest req = request("wrong");

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.deleteAccount(gamer, req));
            assertEquals(150, ex.getTransactionCode().getId());
            assertNull(gamer.getDeletedAt());
        }

        @Test
        @DisplayName("everything identifying the gamer is cleared")
        void testDeleteAccount_whenValid_AnonymisesPersonalData() {
            authService.deleteAccount(gamer, request("correct"));

            assertNotNull(gamer.getDeletedAt());
            assertFalse(gamer.getEmail().contains("me@example.com"));
            assertTrue(gamer.getEmail().endsWith("@deleted.invalid"));
            assertNotEquals("me", gamer.getGamerUsername());
            assertNull(gamer.getAge());
            assertNull(gamer.getCountry());
            assertNull(gamer.getGender());
            assertNull(gamer.getAvatar());
            assertNull(gamer.getFcmToken());
        }

        @Test
        @DisplayName("an approved avatar is removed from the public bucket, not just unlinked")
        void testDeleteAccount_whenApprovedAvatar_DeletesTheObject() {
            gamer.setAvatarKey("avatars/" + gamer.getUserId() + "/a.jpg");
            gamer.setAvatarStatus(AvatarStatus.APPROVED);

            authService.deleteAccount(gamer, request("correct"));

            // The row stops pointing at it *and* the object goes. Only the first would
            // leave a photograph of a real person readable by anyone holding the URL.
            verify(objectStorage).delete(ObjectStorage.Bucket.MEDIA, "avatars/" + gamer.getUserId() + "/a.jpg");
            assertNull(gamer.getAvatarKey());
            assertNull(gamer.getAvatarStatus());
        }

        @Test
        @DisplayName("an unapproved avatar is removed from the private bucket it is still in")
        void testDeleteAccount_whenPendingAvatar_DeletesFromUploads() {
            gamer.setAvatarKey("avatars/" + gamer.getUserId() + "/b.jpg");
            gamer.setAvatarStatus(AvatarStatus.PENDING);

            authService.deleteAccount(gamer, request("correct"));

            // Never promoted, so it is still in uploads. Deleting from the wrong bucket
            // would silently leave it where it is.
            verify(objectStorage).delete(ObjectStorage.Bucket.UPLOADS, "avatars/" + gamer.getUserId() + "/b.jpg");
        }

        @Test
        @DisplayName("an account that never uploaded anything touches storage not at all")
        void testDeleteAccount_whenNoAvatar_DoesNotTouchStorage() {
            authService.deleteAccount(gamer, request("correct"));

            verifyNoInteractions(objectStorage);
        }

        @Test
        @DisplayName("taste data goes, so the recommender stops suggesting the account")
        void testDeleteAccount_whenValid_ClearsRecommenderInputs() {
            authService.deleteAccount(gamer, request("correct"));

            assertTrue(gamer.getKeywords().isEmpty());
            assertTrue(gamer.getLikedgames().isEmpty());
        }

        @Test
        @DisplayName("purchases are erased and both worn slots emptied")
        void testDeleteAccount_whenValid_ClearsCosmetics() {
            authService.deleteAccount(gamer, request("correct"));

            // Unequipping matters as much as deleting: the gamer row survives deletion so
            // other people's content keeps its references, and a row still pointing at a
            // frame whose purchase has been erased is a foreign key to nothing.
            assertNull(gamer.getEquippedFrame());
            assertNull(gamer.getEquippedBanner());
            verify(gamerCosmeticRepository).deleteAllByUserId(gamer.getUserId());
        }

        @Test
        @DisplayName("earned badges go too")
        void testDeleteAccount_whenValid_ClearsBadges() {
            authService.deleteAccount(gamer, request("correct"));

            // What somebody achieved is a record of what they did here, and the showcase is
            // the part of it other people could see.
            verify(gamerBadgeRepository).deleteAllByUserId(gamer.getUserId());
        }

        @Test
        @DisplayName("the account stops authenticating everywhere, not just here")
        void testDeleteAccount_whenValid_DisablesAndRevokes() {
            authService.deleteAccount(gamer, request("correct"));

            // isEnabled() is what the shared JWT filter checks, so this covers all five
            // services without any of them being told.
            assertFalse(gamer.isEnabled());
            assertNotNull(gamer.getTokensValidFrom());
            verify(sessionRepository).deleteAllByEmail("me@example.com");
            verify(verificationCodeRepository).invalidateAllForEmail("me@example.com");
        }

        @Test
        @DisplayName("the old password hash is replaced, not left verifiable")
        void testDeleteAccount_whenValid_ReplacesThePasswordHash() {
            authService.deleteAccount(gamer, request("correct"));

            assertEquals("re-encoded", gamer.getPwd());
        }

        @Test
        void testDeleteAccount_whenAlreadyDeleted_ReturnErrorCode156() {
            authService.deleteAccount(gamer, request("correct"));
            DeleteAccountRequest req = request("correct");

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.deleteAccount(gamer, req));
            assertEquals(156, ex.getTransactionCode().getId());
        }
    }

    @Nested
    class FcmToken {

        private static FcmTokenRequest token(String value) {
            FcmTokenRequest r = new FcmTokenRequest();
            r.setFcmToken(value);
            return r;
        }

        @Test
        @DisplayName("a rotated token is stored, so push keeps working")
        void testUpdateFcmToken_whenChanged_StoresIt() {
            authService.updateFcmToken(gamer, token("new-device-token"));

            assertEquals("new-device-token", gamer.getFcmToken());
            verify(gamerRepository).save(gamer);
        }

        @Test
        @DisplayName("the token is detached from any other account holding it")
        void testUpdateFcmToken_whenChanged_ClearsItElsewhere() {
            authService.updateFcmToken(gamer, token("new-device-token"));

            // Otherwise the previous owner of that handset keeps receiving this gamer's
            // notifications.
            verify(gamerRepository).clearFcmTokenFrom("new-device-token", gamer.getUserId());
        }

        @Test
        void testUpdateFcmToken_whenUnchanged_DoesNothing() {
            authService.updateFcmToken(gamer, token("device-token"));

            verify(gamerRepository, never()).save(any());
            verify(gamerRepository, never()).clearFcmTokenFrom(anyString(), anyString());
        }
    }
}
