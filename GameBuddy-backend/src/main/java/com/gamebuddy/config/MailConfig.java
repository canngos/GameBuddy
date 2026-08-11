package com.gamebuddy.config;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.util.StringUtils;

/**
 * Where verification emails go.
 *
 * <p>Two modes, and the default is the real one:
 *
 * <ul>
 *   <li>{@code gamebuddy.mail.mode=smtp} (default) — Boot's own {@code JavaMailSender}, sending
 *       over the configured SMTP host.
 *   <li>{@code gamebuddy.mail.mode=log} — writes the message to the log and sends nothing.
 * </ul>
 *
 * <p>Log mode exists because registration is otherwise unreachable without a mail server.
 * {@code sendVerificationCode} propagates {@link org.springframework.mail.MailException} so
 * the surrounding transaction rolls back, which is correct — a mail outage must not leave an
 * orphaned account holding an address nobody can prove they own. The consequence is that
 * with no SMTP reachable, signup always fails and no account is ever created, so the first
 * screen of the app cannot be exercised at all.
 *
 * <p>Deliberately a property rather than a Spring profile. A profile is easy to switch on by
 * accident — an inherited {@code SPRING_PROFILES_ACTIVE}, a copied deployment manifest — and
 * the failure is silent: users would register successfully and never receive a code, while
 * anyone reading the logs could take over any address they liked. An explicit mode has to be
 * set to the word {@code log} (case is not significant), and it announces itself loudly at
 * startup. Anything else — including {@code logging}, {@code true} or an empty value — leaves
 * real sending in place, so the unsafe state is only ever reached on purpose.
 */
@Slf4j
@Configuration
public class MailConfig {

    /**
     * Prints verification emails instead of sending them.
     *
     * <p>{@code @Primary} because Boot has already autoconfigured a real sender from
     * {@code spring.mail.host} — which has to stay configured, since without it Boot creates
     * no {@code JavaMailSender} at all and the application refuses to start.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "gamebuddy.mail.mode", havingValue = "log")
    public JavaMailSender loggingMailSender() {
        log.warn("=".repeat(78));
        log.warn("MAIL MODE 'log': verification emails are PRINTED, NOT SENT.");
        log.warn("Anyone who can read these logs can verify any address. Local use only.");
        log.warn("=".repeat(78));
        return new LoggingMailSender();
    }

    /**
     * Refuses to start when real sending is configured but incompletely.
     *
     * <p>This exists because of how the missing piece announced itself. A blank
     * {@code gamebuddy.mail.from} is not caught anywhere near the configuration — it travels
     * all the way to {@code setFrom("")} and surfaces as {@code AddressException: Illegal
     * address in string ''} inside the first registration attempt. The application starts
     * clean, the health check passes, and the fault is invisible until a real person tries
     * to sign up and is told the email could not be sent. Every account creation fails, and
     * nothing says why until somebody reads a stack trace.
     *
     * <p>A missing host or password behaves the same way: fine at boot, broken on first use.
     * So all four settings are checked together and the application refuses to start without
     * them. A container that will not start is noticed immediately; one that starts and
     * cannot register anybody is noticed by users.
     *
     * <p>Skipped entirely in {@code log} mode, which sends nothing and needs none of it.
     */
    @Bean
    public Object smtpConfigurationCheck(
            @Value("${gamebuddy.mail.mode:smtp}") String mode,
            @Value("${gamebuddy.mail.from:}") String from,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {

        if ("log".equalsIgnoreCase(mode)) {
            return new Object();
        }

        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(from)) {
            missing.add("MAIL_FROM (gamebuddy.mail.from)");
        }
        if (!StringUtils.hasText(host)) {
            missing.add("MAIL_HOST (spring.mail.host)");
        }
        if (!StringUtils.hasText(username)) {
            missing.add("SMTP_EMAIL (spring.mail.username)");
        }
        if (!StringUtils.hasText(password)) {
            missing.add("SMTP_EMAIL_PWD (spring.mail.password)");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Mail mode is '" + mode
                    + "' (real sending) but these settings are blank: " + String.join(", ", missing)
                    + ". Set them, or set MAIL_MODE=log for local work — with them missing every"
                    + " registration would fail at the point of sending.");
        }

        // The sender is the one value SMTP will not reject on our behalf: Brevo and every
        // other relay authenticates the login, not the From header, so a malformed address
        // here is only discovered when a message is built.
        if (!from.contains("@") || from.startsWith("@") || from.endsWith("@")) {
            throw new IllegalStateException(
                    "MAIL_FROM is not an email address: '" + from + "'. It becomes the From"
                            + " header of every verification email and must be a sender your relay"
                            + " is allowed to send as.");
        }

        log.info("Mail: sending over {} as {}", host, from);
        return new Object();
    }

    /**
     * A {@link JavaMailSender} that logs.
     *
     * <p>Only the {@code SimpleMailMessage} paths are implemented, because that is all
     * {@code sendVerificationCode} uses. The MIME paths throw rather than quietly doing
     * nothing: if a future code path starts sending real mail, that should fail visibly in
     * local testing rather than silently vanish and be discovered in production.
     */
    private static class LoggingMailSender implements JavaMailSender {

        @Override
        public void send(SimpleMailMessage message) {
            // One line per field rather than toString(), so the six-digit code is easy to
            // spot and copy out of a busy console.
            log.info("--- email (not sent) -------------------------------------------");
            log.info("  to:      {}", String.join(", ", message.getTo() == null ? new String[0] : message.getTo()));
            log.info("  subject: {}", message.getSubject());
            log.info("  body:    {}", message.getText());
            log.info("----------------------------------------------------------------");
        }

        @Override
        public void send(SimpleMailMessage... messages) {
            for (SimpleMailMessage message : messages) {
                send(message);
            }
        }

        @Override
        public MimeMessage createMimeMessage() {
            throw new UnsupportedOperationException("mail mode 'log' handles simple messages only");
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            throw new UnsupportedOperationException("mail mode 'log' handles simple messages only");
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            throw new UnsupportedOperationException("mail mode 'log' handles simple messages only");
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException("mail mode 'log' handles simple messages only");
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) {
            throw new UnsupportedOperationException("mail mode 'log' handles simple messages only");
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) {
            throw new UnsupportedOperationException("mail mode 'log' handles simple messages only");
        }
    }
}
