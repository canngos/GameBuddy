package com.gamebuddy.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.auth.domain.event.ProfileChangedEvent;
import com.gamebuddy.auth.infrastructure.entity.*;
import com.gamebuddy.auth.infrastructure.repository.*;
import com.gamebuddy.auth.interfaces.request.*;
import com.gamebuddy.auth.interfaces.response.*;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.common.security.JwtService;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.repository.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultAuthServiceTest {

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
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender emailSender;

    @Mock
    private ApplicationEventPublisher events;

    /**
     * Real limiters with the production budgets. JUnit builds a fresh test instance per
     * method, so every test starts with empty windows.
     */
    @Spy
    private AuthRateLimiters rateLimiters = new AuthRateLimiters(
            new RateLimiter(10, Duration.ofMinutes(15)),
            new RateLimiter(3, Duration.ofMinutes(15)),
            new RateLimiter(10, Duration.ofMinutes(15)));

    private static final String EMAIL = "test@example.com";
    private static final String GOOD_PASSWORD = "Str0ngPassw0rd";
    private static final String TOKEN = "header.payload.signature";

    private Gamer gamer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "sender", "noreply@gamebuddy.app");

        gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setEmail(EMAIL);
        gamer.setGamerUsername("tester");
        gamer.setPwd("encoded");
        gamer.setRole(Role.USER);
        gamer.setIsVerified(true);
        gamer.setIsRegistered(true);
        gamer.setIsBlocked(false);

        when(jwtService.generateToken(any())).thenReturn(TOKEN);
        when(jwtService.extractExpiration(anyString())).thenReturn(Instant.now().plus(Duration.ofDays(7)));
    }

    private VerificationCode liveCode(int code) {
        VerificationCode vc = new VerificationCode();
        vc.setId(UUID.randomUUID());
        vc.setEmail(EMAIL);
        vc.setCode(code);
        vc.setIsValid(true);
        vc.setAttempts(0);
        vc.setCreatedAt(Instant.now());
        vc.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        return vc;
    }

    // =====================================================================
    // login
    // =====================================================================

    @Nested
    class Login {

        private LoginRequest request(String who, String password) {
            LoginRequest r = new LoginRequest();
            r.setUsernameOrEmail(who);
            r.setPassword(password);
            return r;
        }

        @Test
        @DisplayName("unknown account reports WRONG_PASSWORD, not USER_NOT_FOUND, to prevent enumeration")
        void testLogin_whenUserNotFound_ReturnError108NotEnumerable() {
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            var request = request(EMAIL, GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
            // Was 103 (USER_NOT_FOUND); an attacker could tell registered addresses apart.
            assertEquals(108, ex.getTransactionCode().getId());
        }

        @Test
        void testLogin_whenPasswordIsWrong_ReturnError108() {
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.of(gamer));
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

            var request = request(EMAIL, "wrong");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
            assertEquals(108, ex.getTransactionCode().getId());
        }

        @Test
        void testLogin_whenUserBlocked_ReturnError113() {
            gamer.setIsBlocked(true);
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.of(gamer));

            var request = request(EMAIL, GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
            assertEquals(113, ex.getTransactionCode().getId());
        }

        @Test
        void testLogin_whenUserNotVerified_ReturnError106() {
            gamer.setIsVerified(false);
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.of(gamer));

            var request = request(EMAIL, GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
            assertEquals(106, ex.getTransactionCode().getId());
        }

        @Test
        void testLogin_whenUserNotCompleted_ReturnError109() {
            gamer.setIsRegistered(false);
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.of(gamer));

            var request = request(EMAIL, GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
            assertEquals(109, ex.getTransactionCode().getId());
        }

        @Test
        void testLogin_whenLoginWithEmail_ReturnSuccess() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));

            LoginResponse response = authService.login(request(EMAIL, GOOD_PASSWORD));

            assertEquals("100", response.getStatus().getCode());
            assertEquals(TOKEN, response.getBody().getData().getAccessToken());
            assertEquals(gamer.getUserId(), response.getBody().getData().getUserId());
        }

        @Test
        void testLogin_whenLoginWithUsername_ReturnSuccess() {
            when(gamerRepository.findByGamerUsername("tester")).thenReturn(Optional.of(gamer));

            LoginResponse response = authService.login(request("tester", GOOD_PASSWORD));

            assertEquals("100", response.getStatus().getCode());
            verify(gamerRepository, never()).findByEmail(anyString());
        }

        @Test
        @DisplayName("the session row stores a hash, never the bearer token itself")
        void testLogin_persistsHashedSessionNotRawToken() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));

            authService.login(request(EMAIL, GOOD_PASSWORD));

            ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
            verify(sessionRepository).save(captor.capture());
            Session saved = captor.getValue();
            assertNotEquals(TOKEN, saved.getTokenHash());
            assertEquals(64, saved.getTokenHash().length(), "SHA-256 hex is 64 chars");
            verify(sessionRepository).deleteAllByEmail(EMAIL);
        }

        @Test
        @DisplayName("repeated failures are throttled")
        void testLogin_whenAttemptsExceeded_ReturnRateLimited() {
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            for (int i = 0; i < 10; i++) {
                var request = request(EMAIL, "guess");
                assertThrows(BusinessException.class, () -> authService.login(request));
            }
            var request = request(EMAIL, "guess");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
            assertEquals(146, ex.getTransactionCode().getId());
        }
    }

    // =====================================================================
    // register
    // =====================================================================

    @Nested
    class Register {

        private RegisterRequest request(String password) {
            RegisterRequest r = new RegisterRequest();
            r.setEmail(EMAIL);
            r.setPassword(password);
            r.setFcmToken("fcm");
            return r;
        }

        @Test
        void testRegister_whenVerifiedEmailAlreadyExists_ReturnError101() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));

            var request = request(GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
            assertEquals(101, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("an unverified account can be re-registered instead of being stranded forever")
        void testRegister_whenExistingAccountUnverified_ReclaimsIt() {
            gamer.setIsVerified(false);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(passwordEncoder.encode(anyString())).thenReturn("re-encoded");

            RegisterResponse response = authService.register(request(GOOD_PASSWORD));

            assertEquals("100", response.getStatus().getCode());
            assertEquals(gamer.getUserId(), response.getBody().getData().getUserId());
            assertEquals("re-encoded", gamer.getPwd());
        }

        @Test
        void testRegister_whenEmailSendFails_ReturnError102() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            doThrow(new MailSendException("smtp down")).when(emailSender).send(any(SimpleMailMessage.class));

            var request = request(GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
            assertEquals(102, ex.getTransactionCode().getId());
        }

        @Test
        void testRegister_whenPasswordTooWeak_ReturnError147() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var request = request("a");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
            assertEquals(147, ex.getTransactionCode().getId());
            verify(gamerRepository, never()).save(any());
        }

        @Test
        void testRegister_whenValidRequestProvided_ReturnSuccess() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");

            RegisterResponse response = authService.register(request(GOOD_PASSWORD));

            assertEquals("100", response.getStatus().getCode());
            assertNotNull(response.getBody().getData().getUserId());
            verify(gamerRepository).save(any(Gamer.class));
        }

        @Test
        @DisplayName("registration stores no device token, so the column cannot collide")
        void testRegister_doesNotPersistADeviceToken() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");

            authService.register(request(GOOD_PASSWORD));

            ArgumentCaptor<Gamer> captor = ArgumentCaptor.forClass(Gamer.class);
            verify(gamerRepository).save(captor.capture());
            // The client cannot have a real token yet — permission has not been asked for —
            // so whatever it sends is a placeholder. Storing it gave every not-yet-registered
            // account the same token, and the two lookups that resolve a gamer by token then
            // threw NonUniqueResultException; the one in NotificationDispatcher escaped as a
            // 500 from whichever request had triggered the notification. The device registers
            // itself later through updateFcmToken, which detaches it from any previous owner.
            assertNull(captor.getValue().getFcmToken(), "no device is registered at sign-up");
        }

        @Test
        @DisplayName("issuing a code invalidates every previous one, and persists that")
        void testRegister_invalidatesPreviousCodes() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");

            authService.register(request(GOOD_PASSWORD));

            // Previously an in-memory forEach that was never saved, so every historical
            // code stayed valid indefinitely.
            verify(verificationCodeRepository).invalidateAllForEmail(EMAIL);

            ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
            verify(verificationCodeRepository).save(captor.capture());
            VerificationCode saved = captor.getValue();
            assertNotNull(saved.getExpiresAt(), "codes must expire");
            assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
            assertEquals(0, saved.getAttempts());
            assertTrue(saved.getCode() >= 100000 && saved.getCode() <= 999999);
        }
    }

    // =====================================================================
    // verifyCode
    // =====================================================================

    @Nested
    class VerifyCode {

        private VerifyRequest request(int code) {
            VerifyRequest r = new VerifyRequest();
            r.setEmail(EMAIL);
            r.setVerificationCode(code);
            return r;
        }

        @Test
        void testVerifyCode_whenUserNotFound_ReturnError103() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var request = request(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyCode(request));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testVerifyCode_whenValidCodeNotFound_ReturnError105() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findByEmailAndCodeAndIsValidTrue(EMAIL, 123456))
                    .thenReturn(Optional.empty());
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.empty());

            var request = request(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyCode(request));
            assertEquals(105, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a wrong guess is charged against the live code")
        void testVerifyCode_whenWrongGuess_IncrementsAttempts() {
            VerificationCode live = liveCode(111111);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findByEmailAndCodeAndIsValidTrue(eq(EMAIL), anyInt()))
                    .thenReturn(Optional.empty());
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(live));

            var request = request(999999);
            assertThrows(BusinessException.class, () -> authService.verifyCode(request));

            assertEquals(1, live.getAttempts());
            verify(verificationCodeRepository).save(live);
        }

        @Test
        @DisplayName("the code burns itself out after five wrong guesses")
        void testVerifyCode_whenAttemptsExhausted_InvalidatesCode() {
            VerificationCode live = liveCode(111111);
            live.setAttempts(4);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findByEmailAndCodeAndIsValidTrue(eq(EMAIL), anyInt()))
                    .thenReturn(Optional.empty());
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(live));

            var request = request(999999);
            assertThrows(BusinessException.class, () -> authService.verifyCode(request));

            assertEquals(5, live.getAttempts());
            assertFalse(live.getIsValid(), "code must be burned once the cap is hit");
        }

        @Test
        void testVerifyCode_whenCodeExpired_ReturnError144() {
            VerificationCode expired = liveCode(123456);
            expired.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findByEmailAndCodeAndIsValidTrue(EMAIL, 123456))
                    .thenReturn(Optional.of(expired));

            var request = request(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyCode(request));
            assertEquals(144, ex.getTransactionCode().getId());
            assertFalse(expired.getIsValid());
        }

        @Test
        void testVerifyCode_whenTooManyRequests_ReturnRateLimited() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findByEmailAndCodeAndIsValidTrue(eq(EMAIL), anyInt()))
                    .thenReturn(Optional.empty());
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.empty());

            for (int i = 0; i < 10; i++) {
                var request = request(100001);
                assertThrows(BusinessException.class, () -> authService.verifyCode(request));
            }
            var request = request(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyCode(request));
            assertEquals(146, ex.getTransactionCode().getId());
        }

        @Test
        void testVerifyCode_whenValidRequestProvided_ReturnSuccess() {
            VerificationCode live = liveCode(123456);
            gamer.setIsVerified(false);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findByEmailAndCodeAndIsValidTrue(EMAIL, 123456))
                    .thenReturn(Optional.of(live));

            VerifyResponse response = authService.verifyCode(request(123456));

            assertEquals("100", response.getStatus().getCode());
            assertEquals(TOKEN, response.getBody().getData().getAccessToken());
            assertTrue(gamer.getIsVerified());
            assertFalse(live.getIsValid(), "the consumed code must not be reusable");
            verify(verificationCodeRepository).invalidateAllForEmail(EMAIL);
            assertNotNull(gamer.getTokensValidFrom(), "verifying must revoke older tokens");
        }
    }

    // =====================================================================
    // sendVerificationEmail
    // =====================================================================

    @Nested
    class SendVerificationEmail {

        private SendCodeRequest request(boolean isRegister) {
            SendCodeRequest r = new SendCodeRequest();
            r.setEmail(EMAIL);
            r.setIsRegister(isRegister);
            return r;
        }

        @Test
        void testSendVerificationEmail_whenUserNotFound_ReturnError103() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var request = request(true);
            BusinessException ex =
                    assertThrows(BusinessException.class, () -> authService.sendVerificationEmail(request));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testSendVerificationEmail_whenErrorOccurWhileSendingMail_ReturnCode102() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            doThrow(new MailSendException("smtp down")).when(emailSender).send(any(SimpleMailMessage.class));

            var request = request(true);
            BusinessException ex =
                    assertThrows(BusinessException.class, () -> authService.sendVerificationEmail(request));
            assertEquals(102, ex.getTransactionCode().getId());
        }

        @Test
        void testSendVerificationEmail_whenSendForRegister_ReturnSuccess() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));

            DefaultMessageResponse response = authService.sendVerificationEmail(request(true));
            assertEquals("100", response.getStatus().getCode());
        }

        @Test
        void testSendVerificationEmail_whenSendForForgotPwd_ReturnSuccess() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));

            DefaultMessageResponse response = authService.sendVerificationEmail(request(false));
            assertEquals("100", response.getStatus().getCode());
        }

        @Test
        @DisplayName("code emails are capped so the endpoint cannot be used to mail-bomb")
        void testSendVerificationEmail_whenSpammed_ReturnRateLimited() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));

            for (int i = 0; i < 3; i++) {
                authService.sendVerificationEmail(request(true));
            }
            var request = request(true);
            BusinessException ex =
                    assertThrows(BusinessException.class, () -> authService.sendVerificationEmail(request));
            assertEquals(146, ex.getTransactionCode().getId());
        }
    }

    // =====================================================================
    // validateToken
    // =====================================================================

    @Nested
    class ValidateToken {

        private Session session() {
            Session s = new Session();
            s.setId(UUID.randomUUID());
            s.setEmail(EMAIL);
            s.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
            return s;
        }

        @Test
        void testValidateToken_whenTokenNotFound_ReturnCode111() {
            when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.validateToken(TOKEN));
            assertEquals(111, ex.getTransactionCode().getId());
        }

        @Test
        void testValidateToken_whenUserNotFound_ReturnError103() {
            when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(session()));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.validateToken(TOKEN));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testValidateToken_whenInValidTokenProvided_ReturnCode110() {
            when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(session()));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(jwtService.isTokenValid(TOKEN, gamer)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.validateToken(TOKEN));
            assertEquals(110, ex.getTransactionCode().getId());
        }

        @Test
        void testValidateToken_whenValidTokenProvided_ReturnSuccess() {
            when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(session()));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(jwtService.isTokenValid(TOKEN, gamer)).thenReturn(true);

            TokenResponse response = authService.validateToken(TOKEN);

            assertEquals("100", response.getStatus().getCode());
            assertTrue(response.getBody().getData().getIsValid());
            assertEquals("tester", response.getBody().getData().getUsername());
        }
    }

    // =====================================================================
    // Authenticated operations
    // =====================================================================

    @Nested
    class SetUsername {

        private UsernameRequest request(String username) {
            UsernameRequest r = new UsernameRequest();
            r.setUsername(username);
            return r;
        }

        @Test
        void testSetUsername_whenUserNotFound_ReturnError103() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.empty());

            var request = request("newname");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.setUsername(gamer, request));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testSetUsername_whenUserNotVerified_ReturnError106() {
            gamer.setIsVerified(false);
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));

            var request = request("newname");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.setUsername(gamer, request));
            assertEquals(106, ex.getTransactionCode().getId());
        }

        @Test
        void testSetUsername_whenUsernameAlreadyTakenByAnotherUser_ReturnError107() {
            Gamer other = new Gamer();
            other.setUserId(UUID.randomUUID().toString());
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(gamerRepository.findByGamerUsername("taken")).thenReturn(Optional.of(other));

            var request = request("taken");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.setUsername(gamer, request));
            assertEquals(107, ex.getTransactionCode().getId());
        }

        @Test
        void testSetUsername_whenUserChangesCurrentUsername_ReturnSuccess() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(gamerRepository.findByGamerUsername("tester")).thenReturn(Optional.of(gamer));

            assertEquals(
                    "100",
                    authService
                            .setUsername(gamer, request("tester"))
                            .getStatus()
                            .getCode());
        }

        @Test
        void testSetUsername_whenValidUsernameProvided_ReturnSuccess() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(gamerRepository.findByGamerUsername("fresh")).thenReturn(Optional.empty());

            assertEquals(
                    "100",
                    authService.setUsername(gamer, request("fresh")).getStatus().getCode());
            assertEquals("fresh", gamer.getGamerUsername());
        }
    }

    @Nested
    class Details {

        private DetailsRequest request(int games, int keywords) {
            DetailsRequest r = new DetailsRequest();
            r.setAge(20);
            r.setCountry("TR");
            r.setGender("M");
            r.setAvatar(UUID.randomUUID().toString());
            r.setFavoriteGames(new ArrayList<>(Collections.nCopies(games, "game-1")));
            List<String> keywordIds = new ArrayList<>();
            for (int i = 0; i < keywords; i++) {
                keywordIds.add(UUID.randomUUID().toString());
            }
            r.setKeywords(keywordIds);
            return r;
        }

        private void stubCatalogue() {
            Avatars avatar = new Avatars();
            avatar.setId(UUID.randomUUID());
            when(avatarsRepository.findById(any(UUID.class))).thenReturn(Optional.of(avatar));
            when(gamesRepository.findById(anyString())).thenReturn(Optional.of(new Games()));
            when(keywordsRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Keywords()));
        }

        @Test
        void testDetails_whenUserNotFound_ReturnError103() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.empty());

            var request = request(3, 5);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.details(gamer, request));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        void testDetails_whenUserNotVerified_ReturnError106() {
            gamer.setIsVerified(false);
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));

            var request = request(3, 5);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.details(gamer, request));
            assertEquals(106, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("the documented 3-game / 5-keyword minimum is enforced server-side")
        void testDetails_whenTooFewGames_ReturnInvalidRequest() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));

            var request = request(2, 5);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.details(gamer, request));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        void testDetails_whenTooFewKeywords_ReturnInvalidRequest() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));

            var request = request(3, 4);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.details(gamer, request));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("saving details registers the gamer and defers the recommender refresh to after commit")
        void testDetails_whenValidRequestProvided_ReturnSuccess() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            stubCatalogue();

            DefaultMessageResponse response = authService.details(gamer, request(3, 5));

            assertEquals("100", response.getStatus().getCode());
            assertTrue(gamer.getIsRegistered());

            // An event rather than an inline call: a recommender outage must not roll
            // the profile back.
            ArgumentCaptor<ProfileChangedEvent> captor = ArgumentCaptor.forClass(ProfileChangedEvent.class);
            verify(events).publishEvent(captor.capture());
            assertEquals(gamer.getUserId(), captor.getValue().userId());
        }
    }

    @Nested
    class ChangePassword {

        private ChangePwdRequest request(String current, String next) {
            ChangePwdRequest r = new ChangePwdRequest();
            r.setCurrentPassword(current);
            r.setPassword(next);
            return r;
        }

        @Test
        void testChangePwd_whenUserNotFound_ReturnError103() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.empty());

            var request = request(GOOD_PASSWORD, "N3wPassw0rd");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.changePwd(gamer, request));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("the current password must be supplied, so a stolen token alone is not enough")
        void testChangePwd_whenCurrentPasswordWrong_ReturnError150() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(passwordEncoder.matches("nope", "encoded")).thenReturn(false);

            var request = request("nope", "N3wPassw0rd");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.changePwd(gamer, request));
            assertEquals(150, ex.getTransactionCode().getId());
            verify(gamerRepository, never()).save(any());
        }

        @Test
        void testChangePwd_whenUserPasswordSame_ReturnError112() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(passwordEncoder.matches(anyString(), eq("encoded"))).thenReturn(true);

            var request = request(GOOD_PASSWORD, GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.changePwd(gamer, request));
            assertEquals(112, ex.getTransactionCode().getId());
        }

        @Test
        void testChangePwd_whenNewPasswordWeak_ReturnError147() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(passwordEncoder.matches(GOOD_PASSWORD, "encoded")).thenReturn(true);
            when(passwordEncoder.matches("weak", "encoded")).thenReturn(false);

            var request = request(GOOD_PASSWORD, "weak");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.changePwd(gamer, request));
            assertEquals(147, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("changing the password revokes every outstanding token")
        void testChangePwd_whenValidRequestProvided_RevokesTokens() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(passwordEncoder.matches(GOOD_PASSWORD, "encoded")).thenReturn(true);
            when(passwordEncoder.matches("N3wPassw0rd", "encoded")).thenReturn(false);
            when(passwordEncoder.encode("N3wPassw0rd")).thenReturn("new-encoded");

            DefaultMessageResponse response = authService.changePwd(gamer, request(GOOD_PASSWORD, "N3wPassw0rd"));

            assertEquals("100", response.getStatus().getCode());
            assertEquals("new-encoded", gamer.getPwd());
            assertNotNull(gamer.getTokensValidFrom());
            verify(sessionRepository).deleteAllByEmail(EMAIL);
        }
    }

    @Nested
    class ChangeAvatarAgeGamesKeywords {

        private Avatars avatar() {
            Avatars a = new Avatars();
            a.setId(UUID.randomUUID());
            return a;
        }

        private ChangeAvatarRequest avatarRequest(UUID id) {
            ChangeAvatarRequest r = new ChangeAvatarRequest();
            r.setAvatarId(id.toString());
            return r;
        }

        @Test
        void testChangeAvatar_whenAvatarNotFound_ReturnErrorCode127() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(avatarsRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            var request = avatarRequest(UUID.randomUUID());
            BusinessException ex =
                    assertThrows(BusinessException.class, () -> authService.changeAvatar(gamer, request));
            assertEquals(127, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a malformed UUID is a 400, not the 500 it used to be")
        void testChangeAvatar_whenAvatarIdMalformed_ReturnInvalidRequest() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            ChangeAvatarRequest request = new ChangeAvatarRequest();
            request.setAvatarId("not-a-uuid");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> authService.changeAvatar(gamer, request));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        // The two tests that lived here checked the paid-avatar paywall — that a special
        // avatar was refused unless bought, and accepted once it was. Nothing in the
        // catalogue is for sale any more, so there is no entitlement left to test: every
        // stock avatar is selectable, which is what the case below asserts.

        @Test
        void testChangeAvatar_whenCatalogueAvatarProvided_ReturnSuccess() {
            Avatars free = avatar();
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(avatarsRepository.findById(free.getId())).thenReturn(Optional.of(free));

            assertEquals(
                    "100",
                    authService
                            .changeAvatar(gamer, avatarRequest(free.getId()))
                            .getStatus()
                            .getCode());
        }

        @Test
        void testChangeAge_whenCalled_ReturnSuccess() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            ChangeAgeRequest request = new ChangeAgeRequest();
            request.setAge(25);

            assertEquals(
                    "100", authService.changeAge(gamer, request).getStatus().getCode());
            assertEquals(25, gamer.getAge());
        }

        @Test
        void testChangeGames_whenCalled_ReturnSuccess() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(gamesRepository.findById(anyString())).thenReturn(Optional.of(new Games()));
            ChangeDetailRequest request = new ChangeDetailRequest();
            request.setGamesOrKeywordsList(List.of("g1", "g2", "g3"));

            assertEquals(
                    "100", authService.changeGames(gamer, request).getStatus().getCode());
            verify(events).publishEvent(any(ProfileChangedEvent.class));
        }

        @Test
        void testChangeGames_whenTooFew_ReturnInvalidRequest() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            ChangeDetailRequest request = new ChangeDetailRequest();
            request.setGamesOrKeywordsList(List.of("g1"));

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.changeGames(gamer, request));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        void testChangeKeywords_whenCalled_ReturnSuccess() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(keywordsRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Keywords()));
            ChangeDetailRequest request = new ChangeDetailRequest();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ids.add(UUID.randomUUID().toString());
            }
            request.setGamesOrKeywordsList(ids);

            assertEquals(
                    "100",
                    authService.changeKeywords(gamer, request).getStatus().getCode());
            verify(events).publishEvent(any(ProfileChangedEvent.class));
        }
    }
}
