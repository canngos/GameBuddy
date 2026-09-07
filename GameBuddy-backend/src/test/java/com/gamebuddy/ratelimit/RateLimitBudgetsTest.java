package com.gamebuddy.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamebuddy.auth.config.AuthRateLimitConfig;
import com.gamebuddy.auth.domain.service.AuthRateLimiters;
import com.gamebuddy.billing.config.BillingRateLimitConfig;
import com.gamebuddy.common.ratelimit.Budget;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.lobby.config.LobbyRateLimitConfig;
import com.gamebuddy.match.config.MatchRateLimitConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Floors for every rate limit in the application, in one place.
 *
 * <p>These exist because the budgets were originally chosen by reasoning about how fast
 * someone *ought* to need to go, and every one of them turned out to be under how fast
 * real people actually went — a production tester was refused while swiping at an
 * ordinary pace and while trying to remember a password. Each number below is a run of
 * actions a person can plainly perform, so a future tightening that would refuse them
 * fails here instead of in somebody's hands.
 *
 * <p>Deliberately floors and not equality assertions. Raising a budget is always safe and
 * should not require editing a test; lowering one past what a person does is the mistake
 * being guarded against, and that is the only thing these fail on.
 *
 * <p><strong>The floors are asserted against the real {@code application.yml}</strong>,
 * bound the same way Spring binds it, rather than against the Java field defaults. The
 * YAML is what a deployment actually reads, so it is what has to be right — testing the
 * Java defaults alone would pass happily while the shipped configuration refused
 * everybody. {@link Defaults} then pins the two together, which is what allows the values
 * to be written down twice without them drifting apart.
 *
 * <p>What keeps this honest in the other direction is that these are *floors on a
 * fixed-window counter*, not licences: none of them is what actually bounds abuse. The
 * daily swipe allowance, the one-live-lobby-per-owner rule, the five-guess cap that
 * invalidates a verification code, and password hashing each do that job in their own
 * place, and each is tested where it lives.
 */
class RateLimitBudgetsTest {

    private static final String SOMEONE = "a-user-id";

    // Bound from application.yml exactly as the running application binds it, including
    // the ${ENV:default} placeholders — no environment variables are set here, so what
    // comes out is what a deployment that configures nothing gets.
    private static final MatchRateLimitConfig MATCH = fromYaml("match", new MatchRateLimitConfig());
    private static final LobbyRateLimitConfig LOBBY = fromYaml("lobby", new LobbyRateLimitConfig());
    private static final AuthRateLimitConfig AUTH = fromYaml("auth", new AuthRateLimitConfig());
    private static final BillingRateLimitConfig BILLING = fromYaml("billing", new BillingRateLimitConfig());

    private static <T> T fromYaml(String subtree, T target) {
        return fromYaml(subtree, target, Map.of());
    }

    /**
     * Binds one subtree of the real {@code application.yml} onto {@code target}.
     *
     * <p>{@code environment} stands in for the process environment, ahead of the YAML in
     * precedence — the same arrangement Spring's {@code Environment} has, and therefore
     * the same thing that resolves a {@code ${VAR:default}} placeholder to an override.
     */
    private static <T> T fromYaml(String subtree, T target, Map<String, Object> environment) {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("systemEnvironment", new LinkedHashMap<>(environment)));
        try {
            List<PropertySource<?>> loaded =
                    new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
            loaded.forEach(sources::addLast);
        } catch (IOException cannotRead) {
            throw new UncheckedIOException(cannotRead);
        }
        new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources))
                .bind("gamebuddy.rate-limit." + subtree, Bindable.ofInstance(target));
        return target;
    }

    /** Asserts {@code limiter} takes {@code runLength} back-to-back actions from one key. */
    private static void allowsRunOf(RateLimiter limiter, int runLength, String whatAPersonIsDoing) {
        for (int action = 1; action <= runLength; action++) {
            assertTrue(
                    limiter.tryAcquire(SOMEONE),
                    "refused at " + action + " of " + runLength + ": " + whatAPersonIsDoing);
        }
    }

    @Nested
    @DisplayName("match")
    class Match {

        @Test
        @DisplayName("a minute of fast swiping is not a flood")
        void decisionsSurviveAFastThumb() {
            // Passing on an obvious no is a flick, and a deck is built to be gone through
            // that way. Two a second for a solid minute is past what a thumb sustains.
            allowsRunOf(MATCH.decisionRateLimiter(), 120, "swiping through the deck quickly");
        }
    }

    @Nested
    @DisplayName("lobby")
    class Lobby {

        @Test
        @DisplayName("a day of planning sessions, including the ones cancelled and redone")
        void createsSurviveChangingYourMind() {
            allowsRunOf(LOBBY.lobbyCreateRateLimiter(), 20, "opening lobbies over a day");
        }

        @Test
        @DisplayName("an evening of browsing asks to join more than a handful of lobbies")
        void joinsSurviveABusyFeed() {
            allowsRunOf(LOBBY.lobbyJoinRateLimiter(), 40, "requesting to join while browsing");
        }

        @Test
        @DisplayName("a squad settling on a time talks in bursts of very short messages")
        void messagesSurviveARealConversation() {
            allowsRunOf(LOBBY.lobbyMessageRateLimiter(), 60, "chatting in a lobby");
        }
    }

    @Nested
    @DisplayName("auth")
    class Auth {

        private final AuthRateLimiters limiters = AUTH.authRateLimiters();

        @Test
        @DisplayName("working through the passwords you might have used")
        void loginSurvivesForgettingYourPassword() {
            allowsRunOf(limiters.login(), 14, "trying to remember a password");
        }

        @Test
        @DisplayName("a fumbled code, a fresh one, and fumbling that too")
        void verifySurvivesMistypedCodes() {
            allowsRunOf(limiters.verify(), 20, "typing a mailed verification code");
        }

        @Test
        @DisplayName("a code that is slow to arrive gets asked for more than once")
        void sendCodeSurvivesASlowInbox() {
            // The tightest floor here on purpose: every permit spends somebody else's
            // inbox, and the person flooded is not the person requesting.
            allowsRunOf(limiters.sendCode(), 6, "asking again for a code that has not arrived");
        }

        @Test
        @DisplayName("a reset spends two permits, so the budget must be worth several resets")
        void resetPasswordSurvivesAnAbandonedAttempt() {
            allowsRunOf(limiters.resetPassword(), 20, "resetting a password, twice over");
        }

        @Test
        @DisplayName("linking an account is fiddly the first time")
        void linkSurvivesAFumbledFirstAttempt() {
            // A consent screen abandoned, the wrong Discord account signed in, then the
            // right one. That is a realistic first evening with the feature.
            allowsRunOf(limiters.link(), 10, "linking a Discord account");
        }

        @Test
        void socialSurvivesAConsentSheetAndARetry() {
            // A new account is refused once for the terms and retried with the box ticked,
            // so an honest sign-in can be two calls. Twelve is six of those.
            allowsRunOf(limiters.social(), 12, "signing in with Google or Discord");
        }
    }

    @Nested
    @DisplayName("billing")
    class Billing {

        @Test
        @DisplayName("a code read off a screen is mistyped more than once")
        void redeemSurvivesFumbledCodes() {
            // Eight characters from a deliberately unambiguous alphabet, usually copied
            // off a newsletter or read out loud. Getting it wrong a few times in a row is
            // ordinary; this is also the only limiter here that faces guessing, and the
            // code space rather than this number is what makes guessing hopeless.
            allowsRunOf(BILLING.promoRedeemRateLimiter(), 10, "typing a promotion code");
        }
    }

    /**
     * Every budget is written down twice — once as the Java field default, once as the
     * {@code ${ENV:default}} fallback in {@code application.yml} — and these assert the
     * two say the same thing.
     *
     * <p>Both copies earn their place. The Java default is what a deployment gets when the
     * configuration is absent entirely, which is the case for tests, for a fresh clone,
     * and for any environment where the block was deleted rather than edited. The YAML
     * copy is what makes the knob discoverable: nobody finds a tunable by reading a field
     * initialiser, and the comments explaining *why* each number is what it is belong
     * beside the variable somebody is about to override.
     *
     * <p>The cost of two copies is that they can drift, and a drift here is silent and
     * nasty: the tests above would keep passing against a YAML value nobody meant to
     * change. That is precisely what these catch.
     */
    @Nested
    @DisplayName("the YAML defaults and the Java defaults agree")
    class Defaults {

        private <T> void sameInBothPlaces(T bound, T javaDefaults, String name, Function<T, Budget> budget) {
            Budget fromYaml = budget.apply(bound);
            Budget fromJava = budget.apply(javaDefaults);
            assertEquals(
                    fromJava.permits(),
                    fromYaml.permits(),
                    name + ": application.yml says " + fromYaml + ", the Java default says " + fromJava);
            assertEquals(
                    fromJava.window(),
                    fromYaml.window(),
                    name + ": application.yml says " + fromYaml + ", the Java default says " + fromJava);
        }

        @Test
        void matchAgrees() {
            sameInBothPlaces(MATCH, new MatchRateLimitConfig(), "match.decision", MatchRateLimitConfig::getDecision);
        }

        @Test
        void lobbyAgrees() {
            LobbyRateLimitConfig javaDefaults = new LobbyRateLimitConfig();
            sameInBothPlaces(LOBBY, javaDefaults, "lobby.create", LobbyRateLimitConfig::getCreate);
            sameInBothPlaces(LOBBY, javaDefaults, "lobby.join", LobbyRateLimitConfig::getJoin);
            sameInBothPlaces(LOBBY, javaDefaults, "lobby.message", LobbyRateLimitConfig::getMessage);
        }

        @Test
        void authAgrees() {
            AuthRateLimitConfig javaDefaults = new AuthRateLimitConfig();
            sameInBothPlaces(AUTH, javaDefaults, "auth.login", AuthRateLimitConfig::getLogin);
            sameInBothPlaces(AUTH, javaDefaults, "auth.verify", AuthRateLimitConfig::getVerify);
            sameInBothPlaces(AUTH, javaDefaults, "auth.send-code", AuthRateLimitConfig::getSendCode);
            sameInBothPlaces(AUTH, javaDefaults, "auth.reset-password", AuthRateLimitConfig::getResetPassword);
            sameInBothPlaces(AUTH, javaDefaults, "auth.link", AuthRateLimitConfig::getLink);
            sameInBothPlaces(AUTH, javaDefaults, "auth.social", AuthRateLimitConfig::getSocial);
        }

        @Test
        void billingAgrees() {
            sameInBothPlaces(
                    BILLING,
                    new BillingRateLimitConfig(),
                    "billing.promo-redeem",
                    BillingRateLimitConfig::getPromoRedeem);
        }
    }

    /**
     * The point of the whole arrangement: a limit can be retuned on a running deployment
     * by setting an environment variable, without a rebuild and without a code change.
     *
     * <p>These assert the wiring rather than any particular value. A variable name that is
     * misspelled in {@code application.yml}, or a block moved to a prefix the Java no
     * longer reads, fails here — and would otherwise fail silently in production, where
     * the symptom is an override that appears to have been applied and simply was not.
     */
    @Nested
    @DisplayName("an environment variable overrides the shipped default")
    class EnvironmentOverrides {

        @Test
        void everyKnobIsReachable() {
            MatchRateLimitConfig match = fromYaml(
                    "match",
                    new MatchRateLimitConfig(),
                    Map.of("MATCH_DECISION_PERMITS", "7", "MATCH_DECISION_WINDOW", "30s"));
            assertEquals(7, match.getDecision().permits(), "MATCH_DECISION_PERMITS");
            assertEquals(Duration.ofSeconds(30), match.getDecision().window(), "MATCH_DECISION_WINDOW");

            LobbyRateLimitConfig lobby = fromYaml(
                    "lobby",
                    new LobbyRateLimitConfig(),
                    Map.of(
                            "LOBBY_CREATE_PERMITS", "1",
                            "LOBBY_JOIN_PERMITS", "2",
                            "LOBBY_MESSAGE_PERMITS", "3"));
            assertEquals(1, lobby.getCreate().permits(), "LOBBY_CREATE_PERMITS");
            assertEquals(2, lobby.getJoin().permits(), "LOBBY_JOIN_PERMITS");
            assertEquals(3, lobby.getMessage().permits(), "LOBBY_MESSAGE_PERMITS");

            AuthRateLimitConfig auth = fromYaml(
                    "auth",
                    new AuthRateLimitConfig(),
                    Map.of(
                            "AUTH_LOGIN_PERMITS", "1",
                            "AUTH_VERIFY_PERMITS", "2",
                            "AUTH_SEND_CODE_PERMITS", "3",
                            "AUTH_RESET_PASSWORD_PERMITS", "4",
                            "AUTH_LINK_PERMITS", "6",
                            "AUTH_SOCIAL_PERMITS", "8"));
            assertEquals(1, auth.getLogin().permits(), "AUTH_LOGIN_PERMITS");
            assertEquals(2, auth.getVerify().permits(), "AUTH_VERIFY_PERMITS");
            assertEquals(3, auth.getSendCode().permits(), "AUTH_SEND_CODE_PERMITS");
            assertEquals(4, auth.getResetPassword().permits(), "AUTH_RESET_PASSWORD_PERMITS");
            assertEquals(6, auth.getLink().permits(), "AUTH_LINK_PERMITS");
            assertEquals(8, auth.getSocial().permits(), "AUTH_SOCIAL_PERMITS");

            BillingRateLimitConfig billing = fromYaml(
                    "billing",
                    new BillingRateLimitConfig(),
                    Map.of("BILLING_PROMO_REDEEM_PERMITS", "5", "BILLING_PROMO_REDEEM_WINDOW", "10m"));
            assertEquals(5, billing.getPromoRedeem().permits(), "BILLING_PROMO_REDEEM_PERMITS");
            assertEquals(Duration.ofMinutes(10), billing.getPromoRedeem().window(), "BILLING_PROMO_REDEEM_WINDOW");
        }

        @Test
        @DisplayName("an unset variable arrives empty through compose, and must mean 'leave it alone'")
        void anEmptyOverrideKeepsTheDefault() {
            /*
             * Not a hypothetical. docker-compose.prod.yml forwards each knob as
             * `VAR: ${VAR}`, and Compose resolves an unset variable to the empty string
             * rather than omitting it — so every deployment that has not tuned a limit
             * hands the application `MATCH_DECISION_PERMITS=`.
             *
             * Before Budget took its setters as strings, binding that empty value to an
             * int threw and the application refused to start. An untuned rate limit is
             * the normal case; it must not be a boot failure.
             */
            MatchRateLimitConfig unset = fromYaml(
                    "match",
                    new MatchRateLimitConfig(),
                    Map.of("MATCH_DECISION_PERMITS", "", "MATCH_DECISION_WINDOW", ""));
            assertEquals(
                    new MatchRateLimitConfig().getDecision().permits(),
                    unset.getDecision().permits());
            assertEquals(
                    new MatchRateLimitConfig().getDecision().window(),
                    unset.getDecision().window());
        }

        @Test
        @DisplayName("a nonsensical override stops the application rather than serving it")
        void aBrokenBudgetRefusesToStart() {
            // Zero permits refuses every request and reads, from the outside, exactly like
            // the feature being broken. Better to fail at startup, where the message names
            // the problem, than to have an entire endpoint quietly stop working.
            MatchRateLimitConfig zeroed =
                    fromYaml("match", new MatchRateLimitConfig(), Map.of("MATCH_DECISION_PERMITS", "0"));
            assertThrows(IllegalStateException.class, zeroed::decisionRateLimiter);
        }
    }

    /**
     * The binding above is done by hand, which proves the property names line up but not
     * that Spring itself performs it. These run the real container.
     *
     * <p>Worth its own tests because the ordering is not obvious and is easy to get wrong:
     * these are {@code @Configuration} classes that are *also*
     * {@code @ConfigurationProperties}, so the fields have to be bound by the properties
     * post-processor before the {@code @Bean} methods that read them are invoked. Get that
     * wrong and every limiter is built from an unbound field — which, for an {@code int},
     * means zero permits and an endpoint that refuses everybody. That failure would not
     * show up in any of the tests above.
     */
    @Nested
    @DisplayName("Spring builds the limiters from configuration")
    class SpringWiring {

        /*
         * ConfigurationPropertiesAutoConfiguration is what registers the post-processor
         * that binds a @ConfigurationProperties bean, and it is deliberately explicit
         * here. Without it the runner starts, the beans build, and every field keeps its
         * Java default — so an assertion about an override fails while an assertion about
         * a default passes, which reads exactly like a broken binding and is not one. The
         * real application gets this from Boot's auto-configuration.
         */
        private final ApplicationContextRunner container = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class));

        /** Exhausts {@code limiter} and reports how many actions it allowed. */
        private int budgetOf(RateLimiter limiter) {
            int allowed = 0;
            while (limiter.tryAcquire(SOMEONE)) {
                if (++allowed > 1000) {
                    throw new IllegalStateException("limiter never refused");
                }
            }
            return allowed;
        }

        @Test
        void matchIsWired() {
            container
                    .withUserConfiguration(MatchRateLimitConfig.class)
                    .withPropertyValues("gamebuddy.rate-limit.match.decision.permits=7")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertEquals(7, budgetOf(context.getBean("decisionRateLimiter", RateLimiter.class)));
                    });
        }

        @Test
        void lobbyIsWired() {
            container
                    .withUserConfiguration(LobbyRateLimitConfig.class)
                    .withPropertyValues(
                            "gamebuddy.rate-limit.lobby.create.permits=1",
                            "gamebuddy.rate-limit.lobby.join.permits=2",
                            "gamebuddy.rate-limit.lobby.message.permits=3")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertEquals(1, budgetOf(context.getBean("lobbyCreateRateLimiter", RateLimiter.class)));
                        assertEquals(2, budgetOf(context.getBean("lobbyJoinRateLimiter", RateLimiter.class)));
                        assertEquals(3, budgetOf(context.getBean("lobbyMessageRateLimiter", RateLimiter.class)));
                    });
        }

        @Test
        void authIsWired() {
            container
                    .withUserConfiguration(AuthRateLimitConfig.class)
                    .withPropertyValues(
                            "gamebuddy.rate-limit.auth.login.permits=1",
                            "gamebuddy.rate-limit.auth.verify.permits=2",
                            "gamebuddy.rate-limit.auth.send-code.permits=3",
                            "gamebuddy.rate-limit.auth.reset-password.permits=4",
                            "gamebuddy.rate-limit.auth.link.permits=6")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        AuthRateLimiters limiters = context.getBean(AuthRateLimiters.class);
                        assertEquals(1, budgetOf(limiters.login()));
                        assertEquals(2, budgetOf(limiters.verify()));
                        assertEquals(3, budgetOf(limiters.sendCode()));
                        assertEquals(4, budgetOf(limiters.resetPassword()));
                        assertEquals(6, budgetOf(limiters.link()));
                    });
        }

        @Test
        void billingIsWired() {
            container
                    .withUserConfiguration(BillingRateLimitConfig.class)
                    .withPropertyValues("gamebuddy.rate-limit.billing.promo-redeem.permits=5")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertEquals(5, budgetOf(context.getBean("promoRedeemRateLimiter", RateLimiter.class)));
                    });
        }

        @Test
        @DisplayName("with nothing configured, the container still gets the shipped defaults")
        void unconfiguredStillWorks() {
            container.withUserConfiguration(MatchRateLimitConfig.class).run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(
                        new MatchRateLimitConfig().getDecision().permits(),
                        budgetOf(context.getBean("decisionRateLimiter", RateLimiter.class)));
            });
        }
    }
}
