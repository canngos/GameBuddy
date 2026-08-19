package com.gamebuddy.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.auth.config.AuthRateLimitConfig;
import com.gamebuddy.auth.infrastructure.entity.*;
import com.gamebuddy.auth.infrastructure.repository.*;
import com.gamebuddy.auth.interfaces.request.*;
import com.gamebuddy.auth.interfaces.response.*;
import com.gamebuddy.common.enums.Platform;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.JwtService;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.event.ProfileChangedEvent;
import com.gamebuddy.shared.mail.EmailContent;
import com.gamebuddy.shared.mail.Mailer;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private PasswordResetTicketRepository passwordResetTicketRepository;

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
    private Mailer mailer;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private ObjectStorage objectStorage;

    /**
     * The production bean, built from the real configuration rather than re-declared here.
     * A hand-copied set of budgets drifts from the ones that actually ship the moment
     * somebody tunes them, and then these tests pass while proving nothing about
     * production. JUnit builds a fresh test instance per method, so every test still
     * starts with empty windows.
     */
    @Spy
    private AuthRateLimiters rateLimiters = new AuthRateLimitConfig().authRateLimiters();

    /**
     * The real filter, not a mock. It is a pure function over a word list, so stubbing it
     * would only mean asserting that a stub was called — and the thing worth knowing is
     * whether a username with a slur in it actually gets through.
     */
    @Spy
    private TextModerationService textModeration = new TextModerationService();

    /** Fixed, so an age derived from a date of birth is the same number every run. */
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);

    private static final String EMAIL = "test@example.com";
    private static final String GOOD_PASSWORD = "Str0ngPassw0rd";
    private static final String TOKEN = "header.payload.signature";

    private Gamer gamer;

    @BeforeEach
    void setUp() {
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
        // Sessions are issued through the two-argument form now: a login passes the
        // moment it began, a refresh passes the moment the session originally began.
        when(jwtService.generateToken(any(), any())).thenReturn(TOKEN);
        when(jwtService.extractExpiration(anyString())).thenReturn(Instant.now().plus(Duration.ofDays(7)));
    }

    /** The stand-in for bcrypt output in these tests; see {@link #liveCode}. */
    private static String hashOf(int code) {
        return "bcrypt-of-" + code;
    }

    /**
     * A usable code row.
     *
     * <p>The encoder is a mock, so the row stores a recognisable stand-in rather than real
     * bcrypt output and each test stubs the one comparison it expects to succeed. A wrong
     * guess needs no stub at all: an unstubbed {@code matches} returns false, which is
     * exactly what a wrong guess is.
     */
    private VerificationCode liveCode(int code) {
        VerificationCode vc = new VerificationCode();
        vc.setId(UUID.randomUUID());
        vc.setEmail(EMAIL);
        vc.setCodeHash(hashOf(code));
        vc.setPurpose(CodePurpose.REGISTRATION);
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

        /** What a failed attempt answers with while there is still budget left. */
        private static final int WRONG_PASSWORD = 108;

        private static final int RATE_LIMITED = 146;

        /**
         * A run of retries that must never be throttled.
         *
         * <p>Set one below the shipping budget on purpose. The point is not that fourteen
         * is a magic number of fumbles — it is that tightening the limiter back toward the
         * setting that locked a tester out breaks this test instead of a real sign-in.
         */
        private static final int HONEST_RETRIES = 14;

        private int loginAndGetCode(String password) {
            var request = request(EMAIL, password);
            return assertThrows(BusinessException.class, () -> authService.login(request))
                    .getTransactionCode()
                    .getId();
        }

        /** Rejects everything except the one password the fixture's account really has. */
        private void acceptOnlyTheRealPassword() {
            when(authenticationManager.authenticate(any())).thenAnswer(invocation -> {
                if (!GOOD_PASSWORD.equals(
                        ((UsernamePasswordAuthenticationToken) invocation.getArgument(0)).getCredentials())) {
                    throw new BadCredentialsException("bad");
                }
                return invocation.getArgument(0);
            });
        }

        @Test
        @DisplayName("a run of honest retries is not throttled")
        void testLogin_whenPasswordMistypedSeveralTimes_StillAnswersWrongPassword() {
            // A production tester was locked out by mistyping a password roughly five
            // times in a row with quick taps. That is somebody trying to remember their
            // password, not an attack, and it has to keep answering "wrong password" for
            // as long as anyone would plausibly keep trying.
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            for (int attempt = 1; attempt <= HONEST_RETRIES; attempt++) {
                assertEquals(WRONG_PASSWORD, loginAndGetCode("guess"), "attempt " + attempt + " was throttled");
            }
        }

        @Test
        @DisplayName("repeated failures are throttled eventually")
        void testLogin_whenAttemptsExceeded_ReturnRateLimited() {
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            // Deliberately not asserting the exact budget — that number is a tuning
            // decision owned by AuthRateLimitConfig, and pinning it here just means two
            // places to edit. What must hold is that the door does close at all, and the
            // test above already fixes the point before which it may not.
            int attempts = 0;
            while (loginAndGetCode("guess") == WRONG_PASSWORD) {
                assertTrue(++attempts < 100, "login was never throttled");
            }
        }

        @Test
        @DisplayName("signing in successfully clears the failure budget")
        void testLogin_whenEventuallySuccessful_ResetsThrottle() {
            when(gamerRepository.findByEmail(anyString())).thenReturn(Optional.of(gamer));
            acceptOnlyTheRealPassword();

            for (int attempt = 1; attempt <= HONEST_RETRIES; attempt++) {
                loginAndGetCode("guess");
            }
            assertEquals(
                    "100",
                    authService.login(request(EMAIL, GOOD_PASSWORD)).getStatus().getCode());

            // Without the reset the attempts above would still be counted, and somebody who
            // fumbled their way in would be one stumble from a lockout for the rest of the
            // window.
            assertEquals(WRONG_PASSWORD, loginAndGetCode("guess"));
            assertNotEquals(RATE_LIMITED, loginAndGetCode("guess"));
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
            r.setAcceptedTerms(true);
            return r;
        }

        @Test
        @DisplayName("registration without accepting the terms is refused, and stores nothing")
        void testRegister_whenTermsNotAccepted_ReturnError169() {
            RegisterRequest request = request(GOOD_PASSWORD);
            request.setAcceptedTerms(null);

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
            assertEquals(169, ex.getTransactionCode().getId());
            verify(gamerRepository, never()).save(any());
        }

        @Test
        @DisplayName("the version and the moment of acceptance are recorded, not just a yes")
        void testRegister_whenTermsAccepted_StampsVersionAndTime() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            authService.register(request(GOOD_PASSWORD));

            ArgumentCaptor<Gamer> saved = ArgumentCaptor.forClass(Gamer.class);
            verify(gamerRepository).save(saved.capture());
            assertEquals(TermsPolicy.CURRENT_VERSION, saved.getValue().getTermsVersion());
            assertEquals(Instant.parse("2026-08-07T12:00:00Z"), saved.getValue().getTermsAcceptedAt());
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
            doThrow(new MailSendException("smtp down")).when(mailer).send(anyString(), any(EmailContent.class));

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
            assertEquals(CodePurpose.REGISTRATION, saved.getPurpose(), "a signup code must not reset a password");
            // The code itself is unreadable by design — all that can be checked is that
            // something was hashed rather than the digits being stored.
            assertNotNull(saved.getCodeHash());
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

        /**
         * An unknown address must be indistinguishable from a wrong code.
         *
         * <p>This asserted 103 (USER_NOT_FOUND) when verify still said so out loud, which
         * made the endpoint an account oracle: submit any six digits and the error told you
         * whether the address was registered. It now answers 105 for both, and the test
         * pins that rather than the leak it replaced — an assertion of 103 here passing
         * again would mean the hardening had been undone.
         */
        @Test
        void testVerifyCode_whenUserNotFound_LooksIdenticalToAWrongCode() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var request = request(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyCode(request));
            assertEquals(
                    105,
                    ex.getTransactionCode().getId(),
                    "an unregistered address must answer exactly as a wrong code does");
        }

        @Test
        void testVerifyCode_whenValidCodeNotFound_ReturnError105() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
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
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(expired));

            var request = request(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyCode(request));
            assertEquals(144, ex.getTransactionCode().getId());
            assertFalse(expired.getIsValid());
        }

        @Test
        @DisplayName("guessing codes is throttled eventually, whatever the budget is set to")
        void testVerifyCode_whenTooManyRequests_ReturnRateLimited() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.empty());

            // Deliberately not counting to the budget. That number belongs to
            // AuthRateLimitConfig, and RateLimitBudgetsTest fixes the floor below which it
            // may not fall; this only has to prove the door closes at all.
            int guesses = 0;
            int code;
            do {
                var request = request(100001);
                code = assertThrows(BusinessException.class, () -> authService.verifyCode(request))
                        .getTransactionCode()
                        .getId();
                assertTrue(++guesses < 200, "code guessing was never throttled");
            } while (code != 146);
        }

        @Test
        void testVerifyCode_whenValidRequestProvided_ReturnSuccess() {
            VerificationCode live = liveCode(123456);
            gamer.setIsVerified(false);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(live));
            when(passwordEncoder.matches("123456", hashOf(123456))).thenReturn(true);

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
    // Password reset
    // =====================================================================

    @Nested
    class ResetPassword {

        private ResetVerifyRequest verifyRequest(int code) {
            ResetVerifyRequest r = new ResetVerifyRequest();
            r.setEmail(EMAIL);
            r.setVerificationCode(code);
            return r;
        }

        private ResetPasswordRequest resetRequest(String token, String password) {
            ResetPasswordRequest r = new ResetPasswordRequest();
            r.setEmail(EMAIL);
            r.setResetToken(token);
            r.setPassword(password);
            return r;
        }

        /** A live code issued for a reset rather than for signup. */
        private VerificationCode resetCode(int code) {
            VerificationCode vc = liveCode(code);
            vc.setPurpose(CodePurpose.PASSWORD_RESET);
            return vc;
        }

        private PasswordResetTicket ticket(String tokenHash) {
            PasswordResetTicket t = new PasswordResetTicket();
            t.setId(UUID.randomUUID());
            t.setEmail(EMAIL);
            t.setTokenHash(tokenHash);
            t.setUsed(false);
            t.setCreatedAt(Instant.now());
            t.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
            return t;
        }

        // --- step one: the code ------------------------------------------

        @Test
        @DisplayName("a correct reset code yields a ticket, and burns the code")
        void testVerifyResetCode_whenValid_IssuesTicket() {
            VerificationCode live = resetCode(123456);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(live));
            when(passwordEncoder.matches("123456", hashOf(123456))).thenReturn(true);

            ResetVerifyResponse response = authService.verifyResetCode(verifyRequest(123456));

            assertEquals("100", response.getStatus().getCode());
            assertNotNull(response.getBody().getData().getResetToken());
            assertFalse(live.getIsValid(), "the consumed code must not be reusable");
            verify(verificationCodeRepository).invalidateAllForEmail(EMAIL);
            verify(passwordResetTicketRepository).burnAllForEmail(EMAIL);
        }

        @Test
        @DisplayName("the ticket is stored only as a hash, and is not the token handed out")
        void testVerifyResetCode_whenValid_StoresOnlyTheHash() {
            VerificationCode live = resetCode(123456);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(live));
            when(passwordEncoder.matches("123456", hashOf(123456))).thenReturn(true);

            String token = authService
                    .verifyResetCode(verifyRequest(123456))
                    .getBody()
                    .getData()
                    .getResetToken();

            ArgumentCaptor<PasswordResetTicket> captor = ArgumentCaptor.forClass(PasswordResetTicket.class);
            verify(passwordResetTicketRepository).save(captor.capture());
            PasswordResetTicket saved = captor.getValue();
            assertNotEquals(token, saved.getTokenHash(), "the raw token must never be stored");
            assertEquals(64, saved.getTokenHash().length(), "SHA-256 hex");
            assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
        }

        /**
         * The purpose column earning its place.
         *
         * <p>Before it existed, the code mailed for a reset was redeemable at
         * {@code /auth/verify} — which signs the account in. This is the other direction of
         * the same rule, and it must answer exactly as a wrong code does so that nobody can
         * learn which flow an address is part-way through.
         */
        @Test
        @DisplayName("a signup code cannot reset a password, and is charged as a wrong guess")
        void testVerifyResetCode_whenCodeIsForRegistration_Refuses() {
            VerificationCode live = liveCode(123456); // REGISTRATION
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(live));
            when(passwordEncoder.matches("123456", hashOf(123456))).thenReturn(true);

            var request = verifyRequest(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyResetCode(request));

            assertEquals(105, ex.getTransactionCode().getId());
            assertEquals(1, live.getAttempts(), "a mismatched purpose costs an attempt like any wrong guess");
            verify(passwordResetTicketRepository, never()).save(any());
        }

        @Test
        @DisplayName("and the reverse: a reset code cannot sign anyone in")
        void testVerifyCode_whenCodeIsForPasswordReset_Refuses() {
            VerificationCode live = resetCode(123456);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(live));
            when(passwordEncoder.matches("123456", hashOf(123456))).thenReturn(true);

            var request = new VerifyRequest();
            request.setEmail(EMAIL);
            request.setVerificationCode(123456);

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyCode(request));
            assertEquals(105, ex.getTransactionCode().getId());
            assertFalse(Boolean.TRUE.equals(gamer.getIsVerified()) && gamer.getTokensValidFrom() != null);
        }

        @Test
        void testVerifyResetCode_whenUserNotFound_LooksIdenticalToAWrongCode() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var request = verifyRequest(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyResetCode(request));
            assertEquals(105, ex.getTransactionCode().getId());
        }

        @Test
        void testVerifyResetCode_whenCodeExpired_ReturnError144() {
            VerificationCode expired = resetCode(123456);
            expired.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(expired));

            var request = verifyRequest(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyResetCode(request));
            assertEquals(144, ex.getTransactionCode().getId());
        }

        @Test
        void testVerifyResetCode_whenAttemptsExhausted_ReturnError145() {
            VerificationCode live = resetCode(123456);
            live.setAttempts(5);
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(verificationCodeRepository.findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(EMAIL))
                    .thenReturn(Optional.of(live));

            var request = verifyRequest(123456);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.verifyResetCode(request));
            assertEquals(145, ex.getTransactionCode().getId());
            assertFalse(live.getIsValid());
        }

        // --- step two: the ticket ----------------------------------------

        @Test
        @DisplayName("a valid ticket sets the password and signs every device out")
        void testResetPassword_whenValid_ChangesPasswordAndRevokes() {
            PasswordResetTicket t = ticket("hash");
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(passwordEncoder.matches(eq(GOOD_PASSWORD), anyString())).thenReturn(false);
            when(passwordEncoder.encode(GOOD_PASSWORD)).thenReturn("re-encoded");

            authService.resetPassword(resetRequest("raw-token", GOOD_PASSWORD));

            assertEquals("re-encoded", gamer.getPwd());
            assertNotNull(gamer.getTokensValidFrom(), "a reset must revoke tokens issued before it");
            verify(sessionRepository).deleteAllByEmail(EMAIL);
            assertTrue(t.getUsed(), "the ticket must be single use");
            verify(passwordResetTicketRepository).burnAllForEmail(EMAIL);
            verify(verificationCodeRepository).invalidateAllForEmail(EMAIL);
        }

        @Test
        @DisplayName("the account is told its password changed — the takeover victim's only signal")
        void testResetPassword_whenValid_SendsNotice() {
            PasswordResetTicket t = ticket("hash");
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(passwordEncoder.encode(GOOD_PASSWORD)).thenReturn("re-encoded");

            authService.resetPassword(resetRequest("raw-token", GOOD_PASSWORD));

            ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
            verify(mailer).send(eq(EMAIL), captor.capture());
            assertEquals(
                    Constants.EMAIL_SUBJECT_PASSWORD_CHANGED, captor.getValue().subject());
            // No code in it, and nothing to click. Everything actionable in the reset flow
            // is already visible to whoever holds the mailbox; this one exists so an owner
            // who did not do it finds out, and it must hand an attacker nothing.
            assertFalse(captor.getValue().hasCode());
        }

        /**
         * The notice is the one mail whose failure must not undo anything: the password has
         * already changed and the sessions are already gone.
         */
        @Test
        void testResetPassword_whenNoticeFails_StillSucceeds() {
            PasswordResetTicket t = ticket("hash");
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(passwordEncoder.encode(GOOD_PASSWORD)).thenReturn("re-encoded");
            doThrow(new MailSendException("relay down")).when(mailer).send(anyString(), any(EmailContent.class));

            assertDoesNotThrow(() -> authService.resetPassword(resetRequest("raw-token", GOOD_PASSWORD)));
            assertEquals("re-encoded", gamer.getPwd());
        }

        @Test
        void testResetPassword_whenTicketUnknown_ReturnError105() {
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            var request = resetRequest("nope", GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.resetPassword(request));
            assertEquals(105, ex.getTransactionCode().getId());
        }

        @Test
        void testResetPassword_whenTicketAlreadyUsed_ReturnError105() {
            PasswordResetTicket t = ticket("hash");
            t.setUsed(true);
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));

            var request = resetRequest("raw-token", GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.resetPassword(request));
            assertEquals(105, ex.getTransactionCode().getId());
        }

        @Test
        void testResetPassword_whenTicketExpired_ReturnError144() {
            PasswordResetTicket t = ticket("hash");
            t.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));

            var request = resetRequest("raw-token", GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.resetPassword(request));
            assertEquals(144, ex.getTransactionCode().getId());
        }

        /** A ticket is bound to the address it was issued for. */
        @Test
        void testResetPassword_whenTicketBelongsToAnotherEmail_ReturnError105() {
            PasswordResetTicket t = ticket("hash");
            t.setEmail("someone.else@example.com");
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));

            var request = resetRequest("raw-token", GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.resetPassword(request));
            assertEquals(105, ex.getTransactionCode().getId());
        }

        @Test
        void testResetPassword_whenPasswordWeak_ReturnError147() {
            PasswordResetTicket t = ticket("hash");
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));

            var request = resetRequest("raw-token", "weak");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.resetPassword(request));
            assertEquals(147, ex.getTransactionCode().getId());
            assertNull(gamer.getTokensValidFrom(), "a refused password must change nothing");
        }

        @Test
        void testResetPassword_whenSameAsCurrent_ReturnError112() {
            PasswordResetTicket t = ticket("hash");
            when(passwordResetTicketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            when(passwordEncoder.matches(eq(GOOD_PASSWORD), anyString())).thenReturn(true);

            var request = resetRequest("raw-token", GOOD_PASSWORD);
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.resetPassword(request));
            assertEquals(112, ex.getTransactionCode().getId());
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

        /**
         * An unknown address is answered, not refused, and nothing is sent.
         *
         * <p>This asserted 103 (USER_NOT_FOUND) back when the endpoint said so, which made
         * it two things at once: an oracle for which addresses are registered, and — since
         * a known address does send mail — a way to have somebody else's inbox filled by
         * anyone who knows it. The reply is now identical either way.
         *
         * <p>The silence is half the guarantee, so it is asserted rather than assumed: no
         * code is issued and no mail leaves for an address with no account. The rate
         * limiter above this in the service covers the volume; this covers the target.
         */
        @Test
        void testSendVerificationEmail_whenUserNotFound_SaysTheSameAndSendsNothing() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            var request = request(true);
            DefaultMessageResponse response = assertDoesNotThrow(() -> authService.sendVerificationEmail(request));

            assertNotNull(response);
            verify(mailer, never()).send(anyString(), any(EmailContent.class));
            verify(verificationCodeRepository, never()).save(any(VerificationCode.class));
        }

        @Test
        void testSendVerificationEmail_whenErrorOccurWhileSendingMail_ReturnCode102() {
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(gamer));
            doThrow(new MailSendException("smtp down")).when(mailer).send(anyString(), any(EmailContent.class));

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

            // Budget-agnostic for the same reason as the code-guessing test above: the
            // number lives in AuthRateLimitConfig, and its floor is asserted in
            // RateLimitBudgetsTest. What must hold here is that the endpoint stops.
            int sent = 0;
            while (true) {
                var request = request(true);
                try {
                    authService.sendVerificationEmail(request);
                } catch (BusinessException capped) {
                    assertEquals(146, capped.getTransactionCode().getId());
                    break;
                }
                assertTrue(++sent < 200, "code emails were never capped");
            }
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
            when(gamerRepository.findByGamerUsernameIgnoreCase("taken")).thenReturn(Optional.of(other));

            var request = request("taken");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.setUsername(gamer, request));
            assertEquals(107, ex.getTransactionCode().getId());
        }

        @Test
        void testSetUsername_whenUserChangesCurrentUsername_ReturnSuccess() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(gamerRepository.findByGamerUsernameIgnoreCase("tester")).thenReturn(Optional.of(gamer));

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
            when(gamerRepository.findByGamerUsernameIgnoreCase("fresh")).thenReturn(Optional.empty());

            assertEquals(
                    "100",
                    authService.setUsername(gamer, request("fresh")).getStatus().getCode());
            assertEquals("fresh", gamer.getGamerUsername());
        }

        @Test
        @DisplayName("a name the policy refuses never reaches the uniqueness check")
        void testSetUsername_whenUsernameBreaksThePolicy_ReturnError148() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));

            var request = request("moderator");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.setUsername(gamer, request));
            assertEquals(148, ex.getTransactionCode().getId());
            verify(gamerRepository, never()).findByGamerUsernameIgnoreCase(anyString());
            verify(gamerRepository, never()).save(any());
        }

        @Test
        @DisplayName("a name taken in a different case is taken")
        void testSetUsername_whenUsernameTakenInAnotherCase_ReturnError107() {
            Gamer other = new Gamer();
            other.setUserId(UUID.randomUUID().toString());
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(gamerRepository.findByGamerUsernameIgnoreCase("Taken")).thenReturn(Optional.of(other));

            var request = request("Taken");
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.setUsername(gamer, request));
            assertEquals(107, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("surrounding whitespace is trimmed before anything else looks at it")
        void testSetUsername_whenPaddedWithWhitespace_TrimsBeforeValidating() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(gamerRepository.findByGamerUsernameIgnoreCase("fresh")).thenReturn(Optional.empty());

            assertEquals(
                    "100",
                    authService
                            .setUsername(gamer, request("  fresh  "))
                            .getStatus()
                            .getCode());
            assertEquals("fresh", gamer.getGamerUsername());
        }
    }

    @Nested
    class Details {

        private DetailsRequest request(int games, int keywords) {
            DetailsRequest r = new DetailsRequest();
            r.setBirthDate(LocalDate.of(1998, 8, 24));
            r.setCountry("TR");
            r.setGender("M");
            r.setAvatar(UUID.randomUUID().toString());
            r.setFavoriteGames(new ArrayList<>(Collections.nCopies(games, "game-1")));
            List<String> keywordIds = new ArrayList<>();
            for (int i = 0; i < keywords; i++) {
                keywordIds.add(UUID.randomUUID().toString());
            }
            r.setKeywords(keywordIds);
            r.setPlatforms(List.of("PC"));
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
        @DisplayName("at least one platform is required")
        void testDetails_whenNoPlatforms_ReturnInvalidRequest() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));

            var request = request(3, 5);
            request.setPlatforms(List.of());
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.details(gamer, request));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("an unrecognised platform is refused, not quietly dropped")
        void testDetails_whenUnknownPlatform_ReturnInvalidRequest() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            stubCatalogue();

            var request = request(3, 5);
            request.setPlatforms(List.of("PC", "DREAMCAST"));

            // Skipping it would let the client appear to succeed while saving less than it
            // asked for, and the account holder would find a short list with no error.
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.details(gamer, request));
            assertEquals(148, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("platforms are stored as the enum, case-insensitively, without duplicates")
        void testDetails_whenPlatformsGiven_StoresThem() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            stubCatalogue();

            var request = request(3, 5);
            request.setPlatforms(List.of("pc", "SWITCH", "PC"));

            authService.details(gamer, request);

            assertEquals(Set.of(Platform.PC, Platform.SWITCH), gamer.getPlatforms());
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
        @DisplayName("picking one of ours removes the uploaded photo instead of being hidden behind it")
        void testChangeAvatar_whenUploadExists_ClearsItAndDeletesTheObject() {
            Avatars free = avatar();
            gamer.setAvatarKey("avatars/someone/photo.jpg");
            gamer.setAvatarStatus(AvatarStatus.APPROVED);
            gamer.setAvatarScore(0.1);
            gamer.setAvatarUploadedAt(Instant.parse("2026-08-01T00:00:00Z"));
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(avatarsRepository.findById(free.getId())).thenReturn(Optional.of(free));

            authService.changeAvatar(gamer, avatarRequest(free.getId()));

            // The point of the whole change: AvatarUrls prefers avatarKey, so a catalogue
            // choice that leaves it set is invisible everywhere the avatar is drawn.
            assertEquals(free.getId(), gamer.getAvatar());
            assertNull(gamer.getAvatarKey());
            assertNull(gamer.getAvatarStatus());
            assertNull(gamer.getAvatarScore());
            assertNull(gamer.getAvatarUploadedAt());

            // An approved image lives in MEDIA; anything else never left UPLOADS.
            verify(objectStorage).delete(ObjectStorage.Bucket.MEDIA, "avatars/someone/photo.jpg");
        }

        @Test
        @DisplayName("an upload still awaiting review is deleted from the bucket it is actually in")
        void testChangeAvatar_whenPendingUploadExists_DeletesFromUploads() {
            Avatars free = avatar();
            gamer.setAvatarKey("avatars/someone/pending.jpg");
            gamer.setAvatarStatus(AvatarStatus.PENDING);
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(avatarsRepository.findById(free.getId())).thenReturn(Optional.of(free));

            authService.changeAvatar(gamer, avatarRequest(free.getId()));

            // Clearing the status also takes the account out of the moderation queue,
            // which is keyed on it — there is nothing left to decide about.
            assertNull(gamer.getAvatarStatus());
            verify(objectStorage).delete(ObjectStorage.Bucket.UPLOADS, "avatars/someone/pending.jpg");
        }

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
        @DisplayName("the age is derived from the date, not taken from the request")
        void testChangeAge_whenCalled_ReturnSuccess() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            ChangeAgeRequest request = new ChangeAgeRequest();
            request.setBirthDate(LocalDate.of(1998, 8, 24));

            assertEquals(
                    "100", authService.changeAge(gamer, request).getStatus().getCode());
            // The clock is 2026-08-07 and the birthday falls on the 24th, so this year's
            // has not happened yet: 27, not 28. Exactly the off-by-one a stored age drifts
            // into, and the reason the date is what gets stored.
            assertEquals(27, gamer.getAge());
            assertEquals(LocalDate.of(1998, 8, 24), gamer.getBirthDate());
        }

        @Test
        @DisplayName("a date of birth under 18 is refused")
        void testChangeAge_whenUnderEighteen_ReturnError168() {
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            ChangeAgeRequest request = new ChangeAgeRequest();
            request.setBirthDate(LocalDate.of(2010, 1, 1));

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.changeAge(gamer, request));
            assertEquals(168, ex.getTransactionCode().getId());
            assertNull(gamer.getBirthDate());
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
