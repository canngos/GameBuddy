package com.gamebuddy.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.auth.config.AccountLinkProperties;
import com.gamebuddy.auth.config.AuthRateLimitConfig;
import com.gamebuddy.auth.config.SocialAuthProperties;
import com.gamebuddy.auth.infrastructure.client.DiscordClient;
import com.gamebuddy.auth.infrastructure.client.GoogleIdTokenVerifier;
import com.gamebuddy.auth.infrastructure.entity.GamerAuthIdentity;
import com.gamebuddy.auth.infrastructure.entity.SocialLoginTicket;
import com.gamebuddy.auth.infrastructure.repository.GamerAuthIdentityRepository;
import com.gamebuddy.auth.infrastructure.repository.SocialLoginTicketRepository;
import com.gamebuddy.auth.interfaces.dto.SocialSessionResponseBody;
import com.gamebuddy.common.enums.AuthProvider;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.security.TokenHashing;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Who a social identity becomes, and what it takes to become anybody at all.
 *
 * <p>The interesting half of this class is the refusals. Sign-in is the one endpoint that
 * creates accounts and hands out sessions without anybody having proved possession of a
 * password, so every way of getting it wrong ends with somebody signed in as somebody else.
 */
@DisplayName("DefaultSocialAuthService")
class DefaultSocialAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final String TOKEN = "an.id.token";
    private static final String SUBJECT = "google-subject-1";
    private static final String EMAIL = "player@example.com";
    private static final String SESSION = "a.session.jwt";

    private GamerRepository gamerRepository;
    private GamerAuthIdentityRepository identityRepository;
    private SocialLoginTicketRepository ticketRepository;
    private DiscordClient discordClient;
    private GoogleIdTokenVerifier googleVerifier;
    private SessionIssuer sessionIssuer;
    private SocialAuthProperties properties;
    private AccountLinkProperties linkProperties;

    private DefaultSocialAuthService service;

    @BeforeEach
    void setUp() {
        gamerRepository = mock(GamerRepository.class);
        identityRepository = mock(GamerAuthIdentityRepository.class);
        ticketRepository = mock(SocialLoginTicketRepository.class);
        discordClient = mock(DiscordClient.class);
        googleVerifier = mock(GoogleIdTokenVerifier.class);
        sessionIssuer = mock(SessionIssuer.class);

        properties = new SocialAuthProperties();
        properties.getGoogle().setWebClientId("web-client-id");

        linkProperties = new AccountLinkProperties();
        linkProperties.setPublicBaseUrl("http://localhost:8080");
        linkProperties.getDiscord().setClientId("client-id");
        linkProperties.getDiscord().setClientSecret("client-secret");

        when(googleVerifier.verify(TOKEN))
                .thenReturn(new GoogleIdTokenVerifier.GoogleIdentity(SUBJECT, EMAIL, true, "Player"));
        when(identityRepository.findByProviderAndSubject(any(), any())).thenReturn(Optional.empty());
        when(gamerRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(sessionIssuer.issue(any())).thenReturn(SESSION);

        service = new DefaultSocialAuthService(
                gamerRepository,
                identityRepository,
                ticketRepository,
                discordClient,
                Optional.of(googleVerifier),
                properties,
                linkProperties,
                sessionIssuer,
                new AuthRateLimitConfig().authRateLimiters(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // =======================================================================

    @Nested
    @DisplayName("a known identity")
    class KnownIdentity {

        @Test
        @DisplayName("signs in, and nothing else is consulted")
        void signsIn() {
            Gamer gamer = existingGamer();
            knownIdentity(gamer);

            SocialSessionResponseBody body = service.signInWithGoogle(TOKEN, null);

            assertEquals(SESSION, body.getAccessToken());
            assertEquals(gamer.getUserId(), body.getUserId());
            assertFalse(body.isNewAccount());
            // Not asked for the terms, and not looked up by address: this account agreed
            // long ago, and people change the email on a Google account.
            verify(gamerRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("an account that never finished onboarding is let back in")
        void unregisteredIsNotRefused() {
            Gamer gamer = existingGamer();
            // Password login refuses this with USER_NOT_COMPLETED. Social sign-in must not:
            // it is precisely how somebody gets back to a half-finished account, and the
            // client lands them in onboarding from the stage on their profile.
            gamer.setIsRegistered(false);
            knownIdentity(gamer);

            assertDoesNotThrow(() -> service.signInWithGoogle(TOKEN, null));
        }

        @Test
        @DisplayName("the last-used stamp is touched, so support can tell how somebody gets in")
        void touchesLastUsed() {
            knownIdentity(existingGamer());

            service.signInWithGoogle(TOKEN, null);

            ArgumentCaptor<GamerAuthIdentity> saved = ArgumentCaptor.forClass(GamerAuthIdentity.class);
            verify(identityRepository).save(saved.capture());
            assertEquals(NOW, saved.getValue().getLastUsedAt());
        }

        @Test
        @DisplayName("a deleted account cannot be signed back into")
        void deletedRefused() {
            Gamer gamer = existingGamer();
            gamer.setDeletedAt(NOW);
            knownIdentity(gamer);

            assertEquals(TransactionCode.ACCOUNT_DELETED, refusal(() -> service.signInWithGoogle(TOKEN, null)));
            verify(sessionIssuer, never()).issue(any());
        }

        @Test
        @DisplayName("a blocked account cannot be signed into")
        void blockedRefused() {
            Gamer gamer = existingGamer();
            gamer.setIsBlocked(true);
            knownIdentity(gamer);

            assertEquals(TransactionCode.USER_BLOCKED, refusal(() -> service.signInWithGoogle(TOKEN, null)));
            verify(sessionIssuer, never()).issue(any());
        }
    }

    // =======================================================================

    @Nested
    @DisplayName("an unknown identity")
    class UnknownIdentity {

        @Test
        @DisplayName("an unverified address is refused before anything is written")
        void unverifiedEmailRefused() {
            when(googleVerifier.verify(TOKEN))
                    .thenReturn(new GoogleIdTokenVerifier.GoogleIdentity(SUBJECT, EMAIL, false, "Player"));

            assertEquals(TransactionCode.SOCIAL_EMAIL_UNVERIFIED, refusal(() -> service.signInWithGoogle(TOKEN, true)));
            // The whole point: attaching by address is only safe once somebody else has
            // proved the mailbox. Nothing is created and nothing is attached.
            verify(gamerRepository, never()).save(any());
            verify(identityRepository, never()).save(any());
        }

        @Test
        @DisplayName("attaches to a verified account holding the same address")
        void attachesToExistingAccount() {
            Gamer existing = existingGamer();
            existing.setIsVerified(true);
            existing.setPwd("encoded");
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

            SocialSessionResponseBody body = service.signInWithGoogle(TOKEN, null);

            assertFalse(body.isNewAccount(), "this is the same person, not a second account");
            assertEquals(existing.getUserId(), body.getUserId());
            // The password survives: they registered with one and it still works.
            assertEquals("encoded", existing.getPwd());
            verify(identityRepository).save(any());
        }

        @Test
        @DisplayName("attaching to an unverified account clears its password and verifies it")
        void reclaimsUnverifiedAccount() {
            Gamer squatted = existingGamer();
            squatted.setIsVerified(false);
            squatted.setPwd("whoever-typed-this-never-proved-the-mailbox");
            when(gamerRepository.findByEmail(EMAIL)).thenReturn(Optional.of(squatted));

            service.signInWithGoogle(TOKEN, null);

            // The same rule registration applies when re-claiming an unverified account.
            // Leaving that password would hand a squatter a working login to an address
            // Google has just vouched for.
            assertNull(squatted.getPwd());
            assertTrue(squatted.getIsVerified());
            assertNotNull(squatted.getTokensValidFrom(), "anything already issued stops working");
        }

        @Test
        @DisplayName("a new account needs the terms, and nothing is written without them")
        void newAccountRequiresTerms() {
            assertEquals(TransactionCode.TERMS_NOT_ACCEPTED, refusal(() -> service.signInWithGoogle(TOKEN, null)));

            verify(gamerRepository, never()).save(any());
            verify(identityRepository, never()).save(any());
            verify(sessionIssuer, never()).issue(any());
        }

        @Test
        @DisplayName("with the terms accepted, the account is created verified and unfinished")
        void createsAccount() {
            SocialSessionResponseBody body = service.signInWithGoogle(TOKEN, true);

            assertTrue(body.isNewAccount());

            ArgumentCaptor<Gamer> saved = ArgumentCaptor.forClass(Gamer.class);
            verify(gamerRepository).save(saved.capture());
            Gamer created = saved.getValue();

            assertEquals(EMAIL, created.getEmail());
            // No password at all, so the app can tell "Set a password" from "Change it".
            assertNull(created.getPwd());
            // Google proved the mailbox, which is the whole job the six-digit code does.
            assertTrue(created.getIsVerified());
            // Onboarding still has to happen: the client reads this off the profile stage.
            assertFalse(created.getIsRegistered());
            assertEquals(NOW, created.getTermsAcceptedAt());
            assertEquals(Role.USER, created.getRole());
        }

        @Test
        @DisplayName("the identity is keyed on the provider's subject, not on the address")
        void identityCarriesTheSubject() {
            service.signInWithGoogle(TOKEN, true);

            ArgumentCaptor<GamerAuthIdentity> saved = ArgumentCaptor.forClass(GamerAuthIdentity.class);
            verify(identityRepository).save(saved.capture());
            GamerAuthIdentity identity = saved.getValue();

            // Swapping these two is the kind of mistake that compiles, passes a smoke test and
            // then hands the next person with that email somebody else's account.
            assertEquals(SUBJECT, identity.getSubject());
            assertEquals(EMAIL, identity.getEmailAtLink());
            assertEquals(AuthProvider.GOOGLE, identity.getProvider());
        }

        @Test
        @DisplayName("the address is lower-cased, so one mailbox is one account")
        void normalisesTheAddress() {
            when(googleVerifier.verify(TOKEN))
                    .thenReturn(new GoogleIdTokenVerifier.GoogleIdentity(SUBJECT, "Player@Example.COM", true, "P"));

            service.signInWithGoogle(TOKEN, true);

            ArgumentCaptor<Gamer> saved = ArgumentCaptor.forClass(Gamer.class);
            verify(gamerRepository).save(saved.capture());
            assertEquals(EMAIL, saved.getValue().getEmail());
        }
    }

    // =======================================================================

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("only configured providers are advertised")
        void advertisesWhatIsConfigured() {
            assertEquals(List.of("GOOGLE", "DISCORD"), service.availableProviders());
        }

        @Test
        @DisplayName("with no verifier bean, Google is not offered and not accepted")
        void googleUnconfigured() {
            DefaultSocialAuthService unconfigured = new DefaultSocialAuthService(
                    gamerRepository,
                    identityRepository,
                    ticketRepository,
                    discordClient,
                    Optional.empty(),
                    new SocialAuthProperties(),
                    linkProperties,
                    sessionIssuer,
                    new AuthRateLimitConfig().authRateLimiters(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

            // What is advertised and what is accepted are read off the same fact, so the app
            // can never draw a button the server will refuse.
            assertEquals(List.of("DISCORD"), unconfigured.availableProviders());
            assertEquals(
                    TransactionCode.SOCIAL_TOKEN_INVALID, refusal(() -> unconfigured.signInWithGoogle(TOKEN, true)));
        }
    }

    // =======================================================================

    @Nested
    @DisplayName("the Discord exchange")
    class DiscordExchange {

        private static final String TICKET = "b".repeat(64);

        @Test
        @DisplayName("a redeemable ticket signs in and is spent exactly once")
        void happyPath() {
            redeemableTicket();
            when(ticketRepository.spend(anyString())).thenReturn(1);

            SocialSessionResponseBody body = service.exchange(TICKET, true);

            assertEquals(SESSION, body.getAccessToken());
            verify(ticketRepository).spend(TokenHashing.sha256Hex(TICKET));
        }

        @Test
        @DisplayName("a ticket refused for the terms survives, so the consent sheet can retry")
        void ticketSurvivesATermsRefusal() {
            redeemableTicket();

            assertEquals(TransactionCode.TERMS_NOT_ACCEPTED, refusal(() -> service.exchange(TICKET, null)));

            // Spending it here would make the retry impossible and the consent sheet a dead
            // end — the app cannot know the terms are needed until the server says so.
            verify(ticketRepository, never()).spend(anyString());
        }

        @Test
        @DisplayName("a ticket already spent by a racing request mints nothing")
        void replayRefused() {
            redeemableTicket();
            // The database decides. Two requests carrying the same ticket both get this far;
            // exactly one sees a row count of 1.
            when(ticketRepository.spend(anyString())).thenReturn(0);

            assertEquals(TransactionCode.SOCIAL_TOKEN_INVALID, refusal(() -> service.exchange(TICKET, true)));
        }

        @Test
        @DisplayName("an outbound ticket cannot be exchanged: it carries no identity")
        void outboundTicketIsNotRedeemable() {
            SocialLoginTicket outbound = new SocialLoginTicket();
            outbound.setProvider(AuthProvider.DISCORD);
            outbound.setExpiresAt(NOW.plusSeconds(600));
            // Subject null — this is the one that travelled through Discord and the browser.
            when(ticketRepository.findByTokenHash(anyString())).thenReturn(Optional.of(outbound));

            assertEquals(TransactionCode.SOCIAL_TOKEN_INVALID, refusal(() -> service.exchange(TICKET, true)));
        }

        @Test
        @DisplayName("an unknown ticket says the same thing as a spent one")
        void unknownTicket() {
            when(ticketRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertEquals(TransactionCode.SOCIAL_TOKEN_INVALID, refusal(() -> service.exchange(TICKET, true)));
        }

        private void redeemableTicket() {
            SocialLoginTicket ticket = new SocialLoginTicket();
            ticket.setProvider(AuthProvider.DISCORD);
            ticket.setSubject("discord-snowflake");
            ticket.setEmail(EMAIL);
            ticket.setEmailVerified(true);
            ticket.setExpiresAt(NOW.plusSeconds(600));
            when(ticketRepository.findByTokenHash(TokenHashing.sha256Hex(TICKET)))
                    .thenReturn(Optional.of(ticket));
        }
    }

    // =======================================================================

    @Nested
    @DisplayName("removing a sign-in method")
    class Unlink {

        @Test
        @DisplayName("the last one goes only when a password remains")
        void lastIdentityRefusedWithoutAPassword() {
            Gamer gamer = existingGamer();
            gamer.setPwd(null);
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(identityRepository.findByUserIdAndProvider(gamer.getUserId(), AuthProvider.GOOGLE))
                    .thenReturn(Optional.of(identityFor(gamer)));
            when(identityRepository.countByUserId(gamer.getUserId())).thenReturn(1L);

            assertEquals(TransactionCode.AUTH_IDENTITY_LAST, refusal(() -> service.unlink(gamer, "google")));
            verify(identityRepository, never()).delete(any());
        }

        @Test
        @DisplayName("with a password set, the last one may go")
        void lastIdentityAllowedWithAPassword() {
            Gamer gamer = existingGamer();
            gamer.setPwd("encoded");
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(identityRepository.findByUserIdAndProvider(gamer.getUserId(), AuthProvider.GOOGLE))
                    .thenReturn(Optional.of(identityFor(gamer)));
            when(identityRepository.countByUserId(gamer.getUserId())).thenReturn(1L);

            assertDoesNotThrow(() -> service.unlink(gamer, "google"));
            verify(identityRepository).delete(any());
        }

        @Test
        @DisplayName("one of two may always go")
        void secondIdentityMayGo() {
            Gamer gamer = existingGamer();
            gamer.setPwd(null);
            when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
            when(identityRepository.findByUserIdAndProvider(gamer.getUserId(), AuthProvider.GOOGLE))
                    .thenReturn(Optional.of(identityFor(gamer)));
            when(identityRepository.countByUserId(gamer.getUserId())).thenReturn(2L);

            assertDoesNotThrow(() -> service.unlink(gamer, "google"));
        }

        @Test
        @DisplayName("a provider that was never a way in")
        void notLinked() {
            Gamer gamer = existingGamer();
            when(identityRepository.findByUserIdAndProvider(any(), any())).thenReturn(Optional.empty());

            assertEquals(TransactionCode.ACCOUNT_NOT_LINKED, refusal(() -> service.unlink(gamer, "google")));
        }

        @Test
        @DisplayName("an unknown provider name is a bad request")
        void unknownProvider() {
            assertEquals(TransactionCode.INVALID_REQUEST, refusal(() -> service.unlink(existingGamer(), "steam")));
        }
    }

    // =======================================================================
    // Fixtures
    // =======================================================================

    private Gamer existingGamer() {
        Gamer gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setEmail(EMAIL);
        gamer.setRole(Role.USER);
        gamer.setIsVerified(true);
        gamer.setIsRegistered(true);
        gamer.setIsBlocked(false);
        return gamer;
    }

    private void knownIdentity(Gamer gamer) {
        when(identityRepository.findByProviderAndSubject(AuthProvider.GOOGLE, SUBJECT))
                .thenReturn(Optional.of(identityFor(gamer)));
        when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
    }

    private GamerAuthIdentity identityFor(Gamer gamer) {
        GamerAuthIdentity identity = new GamerAuthIdentity();
        identity.setUserId(gamer.getUserId());
        identity.setProvider(AuthProvider.GOOGLE);
        identity.setSubject(SUBJECT);
        identity.setCreatedAt(NOW);
        return identity;
    }

    private static TransactionCode refusal(Runnable call) {
        return assertThrows(BusinessException.class, call::run).getTransactionCode();
    }
}
