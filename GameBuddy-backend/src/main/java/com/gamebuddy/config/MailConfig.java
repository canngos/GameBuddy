package com.gamebuddy.config;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailParseException;
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
            throw new IllegalStateException("MAIL_FROM is not an email address: '" + from + "'. It becomes the From"
                    + " header of every verification email and must be a sender your relay"
                    + " is allowed to send as.");
        }

        log.info("Mail: sending over {} as {}", host, from);
        return new Object();
    }

    /**
     * A {@link JavaMailSender} that logs.
     *
     * <p>The MIME paths are real, because the branded templates are MIME: a message is
     * {@code multipart/related} around an alternative pair plus the logo, and a sender that
     * refused to build one would make local signup impossible — which is the single thing
     * log mode exists to allow.
     *
     * <p>What gets printed is the {@code text/plain} part. That is the readable half, it is
     * where the six-digit code sits on a line of its own, and it keeps this console output
     * identical in shape to what it was before the templates existed — which matters more
     * than it sounds, because the functional suite reads codes out of exactly these lines
     * (see {@code qa/functional/helpers/db.js}).
     *
     * <p>The {@link MimeMessagePreparator} paths still throw. Nothing here uses them, and a
     * path that quietly sent nothing would be discovered in production rather than locally.
     */
    private static class LoggingMailSender implements JavaMailSender {

        @Override
        public void send(SimpleMailMessage message) {
            print(
                    String.join(", ", message.getTo() == null ? new String[0] : message.getTo()),
                    message.getSubject(),
                    message.getText());
        }

        @Override
        public void send(SimpleMailMessage... messages) {
            for (SimpleMailMessage message : messages) {
                send(message);
            }
        }

        @Override
        public MimeMessage createMimeMessage() {
            // A session with no properties: nothing is ever transmitted, and the message is
            // only ever built, read back and dropped.
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), contentStream);
            } catch (MessagingException e) {
                throw new MailParseException(e);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            try {
                // What a real sender does before transmitting, and not optional here: until
                // it runs, the Content-Type headers have not been written and every part of
                // the message still reports the default, text/plain. Reading the body out of
                // an unsaved message therefore finds the outermost part first and prints a
                // MimeMultipart's toString() where the verification code should be.
                mimeMessage.saveChanges();
                print(
                        Arrays.stream(
                                        mimeMessage.getAllRecipients() == null
                                                ? new Address[0]
                                                : mimeMessage.getAllRecipients())
                                .map(Address::toString)
                                .collect(Collectors.joining(", ")),
                        mimeMessage.getSubject(),
                        plainTextOf(mimeMessage));
            } catch (MessagingException | IOException e) {
                throw new MailParseException(e);
            }
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            for (MimeMessage mimeMessage : mimeMessages) {
                send(mimeMessage);
            }
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) {
            throw new UnsupportedOperationException("mail mode 'log' does not implement MimeMessagePreparator");
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) {
            throw new UnsupportedOperationException("mail mode 'log' does not implement MimeMessagePreparator");
        }

        /** One line per field, so the six-digit code is easy to spot in a busy console. */
        private void print(String to, String subject, String body) {
            log.info("--- email (not sent) -------------------------------------------");
            log.info("  to:      {}", to);
            log.info("  subject: {}", subject);
            log.info("  body:    {}", body);
            log.info("----------------------------------------------------------------");
        }

        /**
         * The first {@code text/plain} part, depth first.
         *
         * <p>Depth first because the structure is nested — the alternative pair is itself a
         * part of the related multipart that carries the logo — and first because in an
         * alternative the plain body precedes the HTML one.
         */
        private String plainTextOf(Part part) throws MessagingException, IOException {
            Object content = part.getContent();
            // Structure before headers: a container is a container whatever its declared
            // type says, so descending on the content object cannot be fooled by a
            // Content-Type that has not been written yet.
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    String found = plainTextOf(multipart.getBodyPart(i));
                    if (found != null) {
                        return found;
                    }
                }
                return null;
            }
            return part.isMimeType("text/plain") && content instanceof String text ? text : null;
        }
    }
}
