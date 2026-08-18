package com.gamebuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gamebuddy.shared.mail.EmailContent;
import com.gamebuddy.shared.mail.Mailer;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The mail mode has to fail safe.
 *
 * <p>The dangerous mistake is the quiet one: a deployment that thinks it is sending
 * verification emails while actually printing them, so every address is takeable by anyone
 * who can read the logs. So the real sender must be what you get unless someone has written
 * {@code log} deliberately.
 */
class MailConfigTest {

    /**
     * A complete, valid SMTP configuration.
     *
     * <p>All four values are supplied because real sending now refuses to start without
     * them — see {@code smtpConfigurationCheck}. These tests are about which sender bean
     * gets chosen, so they hand it a configuration it has no reason to reject; the
     * refusing is tested on its own below.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailConfig.class)
            .withPropertyValues(
                    "spring.mail.host=smtp.example.com",
                    "spring.mail.username=relay-login",
                    "spring.mail.password=secret",
                    "gamebuddy.mail.from=noreply@example.com");

    @Test
    @DisplayName("no mode set means the real sender, not the logging one")
    void defaultsToSmtp() {
        runner.run(context -> assertThat(context.getBean(JavaMailSender.class)).isInstanceOf(JavaMailSenderImpl.class));
    }

    @Test
    @DisplayName("'smtp' is the real sender")
    void smtpIsReal() {
        runner.withPropertyValues("gamebuddy.mail.mode=smtp")
                .run(context ->
                        assertThat(context.getBean(JavaMailSender.class)).isInstanceOf(JavaMailSenderImpl.class));
    }

    @Test
    @DisplayName("a value that merely resembles 'log' is still the real sender")
    void nearMissesDoNotDisableSending() {
        // The direction that matters. Someone has to write the word "log"; nothing that
        // happens to contain it or look vaguely affirmative counts as consent to stop
        // sending verification emails.
        for (String value : new String[] {"logging", "true", "console", "no-mail", ""}) {
            runner.withPropertyValues("gamebuddy.mail.mode=" + value)
                    .run(context -> assertThat(context.getBean(JavaMailSender.class))
                            .as("mode '%s' must not disable real sending", value)
                            .isInstanceOf(JavaMailSenderImpl.class));
        }
    }

    @Test
    @DisplayName("case is not significant, so 'LOG' counts as deliberate too")
    void logModeIsCaseInsensitive() {
        // Tolerated rather than intended: it is still a word someone typed on purpose, and
        // rejecting it would only produce a config that looks disabled but silently sends.
        runner.withPropertyValues("gamebuddy.mail.mode=LOG")
                .run(context ->
                        assertThat(context.getBean(JavaMailSender.class)).isNotInstanceOf(JavaMailSenderImpl.class));
    }

    @Test
    @DisplayName("'log' swaps in the logging sender, and it takes precedence over Boot's")
    void logModeOverridesTheRealSender() {
        runner.withPropertyValues("gamebuddy.mail.mode=log").run(context -> {
            // Two beans exist; @Primary decides which one gets injected. Asserting on the
            // resolved bean is the thing that matters — a logging sender that Boot's
            // autoconfigured one shadowed would leave signup just as broken as before.
            assertThat(context.getBean(JavaMailSender.class)).isNotInstanceOf(JavaMailSenderImpl.class);
        });
    }

    /**
     * Incomplete SMTP settings have to stop the application, not the first user.
     *
     * <p>Written after a blank {@code MAIL_FROM} shipped in a container and did nothing
     * visible: the application started, the health check passed, and it only surfaced as
     * {@code AddressException: Illegal address in string ''} when somebody tried to
     * register — at which point every account creation was failing and nothing said why.
     */
    @Nested
    @DisplayName("incomplete SMTP configuration")
    class IncompleteConfiguration {

        private final ApplicationContextRunner smtp = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(MailConfig.class)
                .withPropertyValues(
                        "spring.mail.host=smtp.example.com",
                        "spring.mail.username=relay-login",
                        "spring.mail.password=secret",
                        "gamebuddy.mail.from=noreply@example.com");

        @Test
        @DisplayName("a blank sender refuses to start, and says which variable is missing")
        void blankFromIsFatal() {
            smtp.withPropertyValues("gamebuddy.mail.from=")
                    .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("MAIL_FROM"));
        }

        @Test
        @DisplayName("blank credentials refuse to start too")
        void blankCredentialsAreFatal() {
            smtp.withPropertyValues("spring.mail.password=")
                    .run(context ->
                            assertThat(context).hasFailed().getFailure().hasMessageContaining("SMTP_EMAIL_PWD"));
            smtp.withPropertyValues("spring.mail.username=")
                    .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("SMTP_EMAIL"));
        }

        @Test
        @DisplayName("a sender that is not an address refuses to start")
        void malformedFromIsFatal() {
            // It becomes the From header of every verification email. A relay authenticates
            // the login, not this, so nothing downstream would catch it.
            smtp.withPropertyValues("gamebuddy.mail.from=noreply")
                    .run(context ->
                            assertThat(context).hasFailed().getFailure().hasMessageContaining("not an email address"));
        }

        @Test
        @DisplayName("log mode needs none of it — that is the whole point of log mode")
        void logModeSkipsTheCheck() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                    .withUserConfiguration(MailConfig.class)
                    .withPropertyValues("spring.mail.host=smtp.example.com", "gamebuddy.mail.mode=log")
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }

    /**
     * Log mode has to survive the messages the application actually sends.
     *
     * <p>It used to refuse every MIME path outright, which was fine while the only mail was
     * a {@code SimpleMailMessage}. The branded templates are multipart, so a log mode that
     * still threw would make local registration impossible — the one thing it exists to
     * allow. Reading the code back out is the other half: the functional suite scrapes it
     * from this console output, because the stored code is a bcrypt hash and cannot be read
     * from the database at all.
     */
    @Nested
    class LogModeHandlesBrandedMail {

        private final ApplicationContextRunner logMode = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(MailConfig.class)
                .withPropertyValues("spring.mail.host=smtp.example.com", "gamebuddy.mail.mode=log");

        @Test
        @DisplayName("a branded multipart message is accepted, not rejected")
        void sendsTheRealThing() {
            logMode.run(context -> {
                JavaMailSender sender = context.getBean(JavaMailSender.class);
                Mailer mailer = new Mailer(sender);
                ReflectionTestUtils.setField(mailer, "from", "noreply@example.com");
                ReflectionTestUtils.setField(mailer, "fromName", "GameBuddy");

                EmailContent content = EmailContent.withCode(
                        "424242 is your GameBuddy code",
                        "Confirm your address.",
                        "Confirm your email",
                        "Enter this code in the app.",
                        "424242",
                        "The code expires in 15 minutes.",
                        "Ignore this if it was not you.");

                assertThatCode(() -> mailer.send("player@example.com", content)).doesNotThrowAnyException();
            });
        }

        @Test
        @DisplayName("the plain-text body is what reaches the console, with the code in it")
        void printsThePlainTextPart() {
            logMode.run(context -> {
                JavaMailSender sender = context.getBean(JavaMailSender.class);
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom("noreply@example.com");
                helper.setTo("player@example.com");
                helper.setSubject("424242 is your GameBuddy code");
                helper.setText("Confirm your email\n\n424242\n", "<html><body>424242</body></html>");

                ListAppender<ILoggingEvent> appender = new ListAppender<>();
                Logger logger = (Logger) LoggerFactory.getLogger(MailConfig.class);
                appender.start();
                logger.addAppender(appender);
                try {
                    sender.send(message);
                } finally {
                    logger.detachAppender(appender);
                }

                String printed = appender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .collect(Collectors.joining("\n"));

                // The exact shape qa/functional/helpers/db.js matches on.
                assertThat(printed).contains("to:      player@example.com");
                assertThat(printed).contains("424242");
                // The failure this replaced: reading an unsaved message finds the outermost
                // part first, calls it text/plain, and prints a MimeMultipart's identity.
                assertThat(printed).doesNotContain("MimeMultipart@");
                assertThat(printed).doesNotContain("<html>");
            });
        }
    }
}
