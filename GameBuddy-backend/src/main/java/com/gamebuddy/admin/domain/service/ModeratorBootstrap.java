package com.gamebuddy.admin.domain.service;

import com.gamebuddy.auth.domain.service.PasswordPolicy;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the moderator account on first start, from the environment.
 *
 * <p>There is no sign-up flow for staff and there should not be one: an endpoint that mints
 * an administrator is a permanent liability, and the alternative — a checked-in SQL
 * insert — puts a password hash in the repository and in every clone of it. The credentials
 * come from {@code MODERATOR_EMAIL} and {@code MODERATOR_PASSWORD}, which live in
 * {@code .env} locally and in the host's environment in production, and are read once.
 *
 * <p><strong>Idempotent, and it never rotates anything.</strong> If an ADMIN already exists
 * this does nothing at all, including when the configured password differs — a restart with
 * a stale environment variable must not silently change the moderator's password, and a
 * compromised environment must not be able to take the account over. Changing the password
 * is done through the ordinary change-password endpoint, as that account.
 *
 * <p>The account deliberately skips onboarding: no age, no games, no keywords. It is not a
 * participant — {@link Gamer#isDiscoverable()} keeps it out of the deck and out of every
 * other user-facing surface — so a profile for it would be a profile nobody can ever see.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModeratorBootstrap implements ApplicationRunner {

    private final GamerRepository gamerRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${gamebuddy.moderator.email:}")
    private String email;

    @Value("${gamebuddy.moderator.password:}")
    private String password;

    @Value("${gamebuddy.moderator.username:moderator}")
    private String username;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("No moderator credentials configured; skipping moderator bootstrap");
            return;
        }

        String normalised = email.trim().toLowerCase(Locale.ROOT);
        if (gamerRepository.findByEmail(normalised).isPresent()) {
            // Covers both "the moderator already exists" and the more interesting case of
            // the address belonging to an ordinary account. Silently promoting a user to
            // administrator because an environment variable named them is exactly the
            // privilege escalation this class must not perform.
            log.info("Moderator bootstrap skipped: an account already exists for that address");
            return;
        }

        // The same rules the sign-up form applies. A staff account is the last one that
        // should be allowed a weaker password than the users it can ban.
        PasswordPolicy.validate(password);

        Gamer moderator = new Gamer();
        moderator.setUserId(UUID.randomUUID().toString());
        moderator.setEmail(normalised);
        moderator.setGamerUsername(username);
        moderator.setPwd(passwordEncoder.encode(password));
        moderator.setRole(Role.ADMIN);
        // Verified and registered: both gates exist to stop a half-finished account being
        // used, and this one is finished — there is simply no profile step for it.
        moderator.setIsVerified(true);
        moderator.setIsRegistered(true);
        moderator.setIsBlocked(false);
        // createdDate is stamped by @CreationTimestamp, like every other account.

        gamerRepository.save(moderator);
        // The address is enough to confirm it worked. The password is never logged, and
        // this line is the only evidence the account was created at all.
        log.info("Created the moderator account for {}", normalised);
    }
}
