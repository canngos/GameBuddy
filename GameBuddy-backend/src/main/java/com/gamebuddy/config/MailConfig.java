package com.gamebuddy.config;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

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
