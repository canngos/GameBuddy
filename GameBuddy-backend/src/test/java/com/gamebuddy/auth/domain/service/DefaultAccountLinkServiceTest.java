package com.gamebuddy.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.auth.config.AccountLinkProperties;
import com.gamebuddy.auth.infrastructure.client.DiscordClient;
import com.gamebuddy.auth.infrastructure.entity.AccountLinkTicket;
import com.gamebuddy.auth.infrastructure.repository.AccountLinkTicketRepository;
import com.gamebuddy.auth.interfaces.request.LinkVisibilityRequest;
import com.gamebuddy.common.enums.LinkVisibility;
import com.gamebuddy.common.enums.LinkedProvider;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.common.security.TokenHashing;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerLinkedAccount;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.repository.GamerLinkedAccountRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The checks that make a link mean something.
 *
 * <p>Almost every test here is about a callback that should be refused, which is the right
 * balance for this feature: the happy path is two HTTP calls and a row, and everything that
 * could go wrong is somebody arriving at a public endpoint with a query string they wrote
 * themselves.
 *
 * <p>{@link TextModerationService} is real rather than mocked. It has no dependencies, and
 * the question "does a profane handle actually get withheld" is only answered by the filter
 * that ships.
 */
class DefaultAccountLinkServiceTest {

    private static final String USER = "gamer-1";
    private static final String OTHER_USER = "gamer-2";
    private static final String TOKEN = "a".repeat(64);
    private static final String BASE = "http://localhost:8080";

    private AccountLinkTicketRepository ticketRepository;
    private GamerLinkedAccountRepository linkedAccountRepository;
    private GamerRepository gamerRepository;
    private DiscordClient discordClient;
    private AccountLinkProperties properties;

    private DefaultAccountLinkService service;
    private Gamer principal;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(AccountLinkTicketRepository.class);
        linkedAccountRepository = mock(GamerLinkedAccountRepository.class);
        gamerRepository = mock(GamerRepository.class);
        discordClient = mock(DiscordClient.class);

        properties = new AccountLinkProperties();
        properties.setPublicBaseUrl(BASE);
        properties.getDiscord().setClientId("client-id");
        properties.getDiscord().setClientSecret("client-secret");

        principal = new Gamer();
        principal.setUserId(USER);

        when(gamerRepository.findById(USER)).thenReturn(Optional.of(principal));
        when(linkedAccountRepository.findByProviderAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(linkedAccountRepository.findByGamer_UserIdAndProvider(any(), any()))
                .thenReturn(Optional.empty());

        service = new DefaultAccountLinkService(
                ticketRepository,
                linkedAccountRepository,
                gamerRepository,
                discordClient,
                properties,
                new TextModerationService(),
                generousLimiters());
    }

    // =======================================================================
    // Starting
    // =======================================================================

    @Nested
    @DisplayName("starting a link")
    class Starting {

        @Test
        @DisplayName("only configured providers are advertised to the app")
        void advertisesOnlyWhatIsConfigured() {
            // A provider with no credentials must not be drawn at all — a button whose only
            // outcome is an error reads as a broken app rather than an unconfigured one.

            List<String> offered =
                    service.availableProviders().getBody().getData().getProviders();

            assertEquals(List.of("DISCORD"), offered);
        }

        @Test
        @DisplayName("nothing is advertised when nothing is configured")
        void advertisesNothingWhenUnconfigured() {
            properties.getDiscord().setClientSecret(null);

            assertTrue(service.availableProviders()
                    .getBody()
                    .getData()
                    .getProviders()
                    .isEmpty());
        }

        @Test
        @DisplayName("what is advertised is what start accepts")
        void advertisedProvidersAreTheOnesThatWork() {
            // What is offered and what start accepts are read off the same predicate, or the
            // app draws a row the server then refuses — the exact drift this catches.
            assertDoesNotThrow(() -> service.startLink(principal, "discord"));

            properties.getDiscord().setClientSecret(null);
            assertTrue(service.availableProviders()
                    .getBody()
                    .getData()
                    .getProviders()
                    .isEmpty());
            assertThrows(BusinessException.class, () -> service.startLink(principal, "discord"));
        }

        @Test
        @DisplayName("hands back an authorize URL carrying the ticket as state")
        void discordUrlCarriesTheTicket() {
            String url =
                    service.startLink(principal, "discord").getBody().getData().getAuthorizeUrl();

            ArgumentCaptor<AccountLinkTicket> saved = ArgumentCaptor.forClass(AccountLinkTicket.class);
            verify(ticketRepository).save(saved.capture());

            assertTrue(url.startsWith("https://discord.com/oauth2/authorize"));
            assertTrue(url.contains("scope=identify"), "only the identify scope is ever requested");
            assertTrue(url.contains("state="), "the ticket travels as OAuth state");
            assertFalse(url.contains(saved.getValue().getTokenHash()), "the URL carries the token, not its hash");
            assertEquals(USER, saved.getValue().getUserId());
            assertEquals(LinkedProvider.DISCORD, saved.getValue().getProvider());
        }

        @Test
        @DisplayName("burns outstanding tickets before minting a new one")
        void abandonedAttemptsAreClosed() {
            service.startLink(principal, "discord");
            verify(ticketRepository).burnAllFor(USER, LinkedProvider.DISCORD);
        }

        @Test
        @DisplayName("an unknown provider is a bad request, not a redirect to nowhere")
        void unknownProviderRefused() {
            BusinessException e = assertThrows(BusinessException.class, () -> service.startLink(principal, "twitch"));
            assertEquals(TransactionCode.INVALID_REQUEST, e.getTransactionCode());
        }

        @Test
        @DisplayName("a provider with no credentials refuses here rather than at the consent screen")
        void unconfiguredProviderRefused() {
            properties.getDiscord().setClientSecret(null);

            BusinessException e = assertThrows(BusinessException.class, () -> service.startLink(principal, "discord"));
            assertEquals(TransactionCode.ACCOUNT_LINK_FAILED, e.getTransactionCode());
            verify(ticketRepository, never()).save(any());
        }
    }

    // =======================================================================
    // Discord callback
    // =======================================================================

    @Nested
    @DisplayName("the Discord callback")
    class Discord {

        @BeforeEach
        void discordTicket() {
            usableTicket(LinkedProvider.DISCORD, USER);
            when(discordClient.exchangeCode(any())).thenReturn(new DiscordClient.TokenResponse("access"));
        }

        @Test
        @DisplayName("stores the account and defaults it to matches-only")
        void happyPath() {
            when(discordClient.currentUser("Bearer access"))
                    .thenReturn(new DiscordClient.DiscordUser("42", "handle", "Display Name", null, null));

            String redirect = service.completeDiscordLink("code", TOKEN);

            GamerLinkedAccount saved = captureSavedLink();
            assertEquals("42", saved.getExternalId());
            assertEquals("Display Name", saved.getHandle(), "the display name wins over the raw handle");
            assertEquals(LinkVisibility.MATCHES, saved.getVisibility(), "a contact detail starts private");
            assertTrue(redirect.contains("status=ok"));
        }

        @Test
        @DisplayName("falls back to the username when no display name is set")
        void displayNameFallback() {
            when(discordClient.currentUser(any()))
                    .thenReturn(new DiscordClient.DiscordUser("42", "handle", null, null, null));

            service.completeDiscordLink("code", TOKEN);

            assertEquals("handle", captureSavedLink().getHandle());
        }

        @Test
        @DisplayName("a profane handle is withheld, but the link still stands")
        void profaneHandleIsWithheld() {
            when(discordClient.currentUser(any()))
                    .thenReturn(new DiscordClient.DiscordUser("42", "handle", "nigger", null, null));

            service.completeDiscordLink("code", TOKEN);

            GamerLinkedAccount saved = captureSavedLink();
            assertNull(saved.getHandle(), "never displayed, and never displayed masked either");
            assertEquals("42", saved.getExternalId(), "what was verified is still verified");
        }

        @Test
        @DisplayName("pressing cancel is reported as cancelled, not as a failure")
        void cancelledConsent() {
            String redirect = service.completeDiscordLink(null, TOKEN);

            assertTrue(redirect.startsWith("gamebuddy://settings/linked"));
            // Not "failed": the app shows an error toast for that, and this was a deliberate
            // decision rather than something going wrong.
            assertTrue(redirect.contains("status=cancelled"));
            verify(linkedAccountRepository, never()).save(any());
            // And the ticket is untouched, so tapping Link again works straight away.
            verify(ticketRepository, never()).spend(any());
        }

        @Test
        @DisplayName("an account already claimed by somebody else is refused by name")
        void alreadyLinkedElsewhere() {
            Gamer other = new Gamer();
            other.setUserId(OTHER_USER);
            GamerLinkedAccount theirs = new GamerLinkedAccount();
            theirs.setGamer(other);
            when(linkedAccountRepository.findByProviderAndExternalId(LinkedProvider.DISCORD, "42"))
                    .thenReturn(Optional.of(theirs));
            when(discordClient.currentUser(any()))
                    .thenReturn(new DiscordClient.DiscordUser("42", "handle", null, null, null));

            String redirect = service.completeDiscordLink("code", TOKEN);

            assertTrue(redirect.contains("status=already_linked"), "the one failure a user can act on");
            verify(linkedAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("a provider that fails is not a stack trace in a browser tab")
        void providerFailure() {
            when(discordClient.exchangeCode(any())).thenThrow(new IllegalStateException("Discord is down"));

            String redirect = service.completeDiscordLink("code", TOKEN);

            assertTrue(redirect.startsWith("gamebuddy://settings/linked"));
            assertTrue(redirect.contains("status=failed"));
        }
    }

    // =======================================================================
    // Tickets
    // =======================================================================

    @Nested
    @DisplayName("the link ticket")
    class Tickets {

        @Test
        @DisplayName("an unknown ticket links nothing")
        void unknownTicket() {
            when(ticketRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertTrue(service.completeDiscordLink("code", "made-up").contains("status=failed"));
            verify(linkedAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("a spent ticket cannot be replayed")
        void spentTicket() {
            usableTicket(LinkedProvider.DISCORD, USER);
            // Losing the conditional update is what "already spent" looks like: the row was
            // there and readable, and somebody else claimed it first.
            when(ticketRepository.spend(TokenHashing.sha256Hex(TOKEN))).thenReturn(0);

            assertTrue(service.completeDiscordLink("code", TOKEN).contains("status=failed"));
            verify(linkedAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("two callbacks racing the same ticket: only one links")
        void onlyOneCallerSpendsATicket() {
            usableTicket(LinkedProvider.DISCORD, USER);
            when(discordClient.exchangeCode(any())).thenReturn(new DiscordClient.TokenResponse("access"));
            when(discordClient.currentUser(any()))
                    .thenReturn(new DiscordClient.DiscordUser("42", "handle", null, null, null));
            // The database hands the row to the first caller and refuses the second, which is
            // the whole reason this is a conditional update rather than a read then a write.
            when(ticketRepository.spend(TokenHashing.sha256Hex(TOKEN))).thenReturn(1, 0);

            assertTrue(service.completeDiscordLink("code", TOKEN).contains("status=ok"));
            assertTrue(service.completeDiscordLink("code", TOKEN).contains("status=failed"));

            verify(linkedAccountRepository, times(1)).save(any());
        }

        // A ticket minted for another provider is refused by `redeem`, and that guard is
        // still in the service — but it cannot be exercised while LinkedProvider has a
        // single constant. Restore this test when a second provider is added.

        @Test
        @DisplayName("an expired ticket cannot be presented late")
        void expiredTicket() {
            AccountLinkTicket ticket = usableTicket(LinkedProvider.DISCORD, USER);
            ticket.setExpiresAt(Instant.now().minusSeconds(1));

            assertTrue(service.completeDiscordLink("code", TOKEN).contains("status=failed"));
            verify(linkedAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("is burned even when the link that follows fails")
        void burnedOnFailure() {
            usableTicket(LinkedProvider.DISCORD, USER);
            when(discordClient.exchangeCode(any())).thenThrow(new IllegalStateException("down"));

            service.completeDiscordLink("code", TOKEN);

            // Spent before the provider is called, so a failed attempt leaves no second go
            // behind. The catch blocks return a redirect rather than rethrowing, which is what
            // lets this commit instead of rolling back with the failure.
            verify(ticketRepository).spend(TokenHashing.sha256Hex(TOKEN));
        }
    }

    // =======================================================================
    // Managing a link
    // =======================================================================

    @Nested
    @DisplayName("managing an existing link")
    class Managing {

        @Test
        @DisplayName("unlinking something that was never linked says so")
        void unlinkWithoutALink() {
            BusinessException e = assertThrows(BusinessException.class, () -> service.unlink(principal, "discord"));
            assertEquals(TransactionCode.ACCOUNT_NOT_LINKED, e.getTransactionCode());
        }

        @Test
        @DisplayName("visibility can be opened up")
        void visibilityIsSettable() {
            GamerLinkedAccount link = existingLink(LinkedProvider.DISCORD);
            LinkVisibilityRequest request = new LinkVisibilityRequest();
            request.setVisibility("PUBLIC");

            service.setVisibility(principal, "discord", request);

            assertEquals(LinkVisibility.PUBLIC, link.getVisibility());
        }

        @Test
        @DisplayName("an unknown visibility is refused rather than silently ignored")
        void unknownVisibilityRefused() {
            existingLink(LinkedProvider.DISCORD);
            LinkVisibilityRequest request = new LinkVisibilityRequest();
            request.setVisibility("EVERYONE");

            BusinessException e =
                    assertThrows(BusinessException.class, () -> service.setVisibility(principal, "discord", request));
            assertEquals(TransactionCode.INVALID_REQUEST, e.getTransactionCode());
        }
    }

    // =======================================================================
    // Visibility, which is enforced on the entity and read by the profile
    // =======================================================================

    @Nested
    @DisplayName("who can see a handle")
    class Visibility {

        private final Gamer owner = gamer(USER);
        private final Gamer stranger = gamer(OTHER_USER);

        @Test
        @DisplayName("the owner always sees their own")
        void ownerSeesTheirOwn() {
            assertTrue(link(LinkVisibility.MATCHES).isVisibleTo(owner, owner));
        }

        @Test
        @DisplayName("a stranger does not see a matches-only handle")
        void strangerIsRefused() {
            assertFalse(link(LinkVisibility.MATCHES).isVisibleTo(owner, stranger));
        }

        @Test
        @DisplayName("a match does")
        void matchIsAllowed() {
            owner.getApprovedMatches().add(stranger);
            stranger.getApprovedMatches().add(owner);

            assertTrue(link(LinkVisibility.MATCHES).isVisibleTo(owner, stranger));
        }

        @Test
        @DisplayName("a one-sided swipe is not a match")
        void oneSidedSwipeIsNotAMatch() {
            owner.getApprovedMatches().add(stranger);

            assertFalse(link(LinkVisibility.MATCHES).isVisibleTo(owner, stranger));
        }

        @Test
        @DisplayName("public means public")
        void publicIsVisibleToAnyone() {
            assertTrue(link(LinkVisibility.PUBLIC).isVisibleTo(owner, stranger));
        }

        private GamerLinkedAccount link(LinkVisibility visibility) {
            GamerLinkedAccount link = new GamerLinkedAccount();
            link.setProvider(LinkedProvider.DISCORD);
            link.setVisibility(visibility);
            return link;
        }

        private Gamer gamer(String id) {
            Gamer gamer = new Gamer();
            gamer.setUserId(id);
            return gamer;
        }
    }

    // =======================================================================
    // Fixtures
    // =======================================================================

    private AccountLinkTicket usableTicket(LinkedProvider provider, String userId) {
        AccountLinkTicket ticket = new AccountLinkTicket();
        ticket.setUserId(userId);
        ticket.setProvider(provider);
        ticket.setTokenHash(TokenHashing.sha256Hex(TOKEN));
        ticket.setUsed(false);
        ticket.setCreatedAt(Instant.now());
        ticket.setExpiresAt(Instant.now().plusSeconds(600));
        when(ticketRepository.findByTokenHash(TokenHashing.sha256Hex(TOKEN))).thenReturn(Optional.of(ticket));
        // The conditional update is what actually spends it; one row means this caller won.
        when(ticketRepository.spend(TokenHashing.sha256Hex(TOKEN))).thenReturn(1);
        return ticket;
    }

    private AccountLinkTicket usableTicketFor(String token, LinkedProvider provider) {
        AccountLinkTicket ticket = new AccountLinkTicket();
        ticket.setUserId(USER);
        ticket.setProvider(provider);
        ticket.setTokenHash(TokenHashing.sha256Hex(token));
        ticket.setUsed(false);
        ticket.setCreatedAt(Instant.now());
        ticket.setExpiresAt(Instant.now().plusSeconds(600));
        when(ticketRepository.findByTokenHash(TokenHashing.sha256Hex(token))).thenReturn(Optional.of(ticket));
        when(ticketRepository.spend(TokenHashing.sha256Hex(token))).thenReturn(1);
        return ticket;
    }

    private GamerLinkedAccount existingLink(LinkedProvider provider) {
        GamerLinkedAccount link = new GamerLinkedAccount();
        link.setGamer(principal);
        link.setProvider(provider);
        link.setVisibility(LinkVisibility.MATCHES);
        when(linkedAccountRepository.findByGamer_UserIdAndProvider(USER, provider))
                .thenReturn(Optional.of(link));
        return link;
    }

    private GamerLinkedAccount captureSavedLink() {
        ArgumentCaptor<GamerLinkedAccount> saved = ArgumentCaptor.forClass(GamerLinkedAccount.class);
        verify(linkedAccountRepository).save(saved.capture());
        return saved.getValue();
    }

    /** Budgets are tested in RateLimitBudgetsTest; nothing here should be refused for pace. */
    private static AuthRateLimiters generousLimiters() {
        return new AuthRateLimiters(limiter(), limiter(), limiter(), limiter(), limiter(), limiter());
    }

    private static RateLimiter limiter() {
        return new RateLimiter(1000, Duration.ofMinutes(15));
    }
}
