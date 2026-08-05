package com.gamebuddy.config;

import com.gamebuddy.notif.domain.event.LastActiveTracker;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Application-wide wiring.
 *
 * <p>Four services each declared their own copy of these beans against their own copy of
 * {@code Gamer}. One copy now, against the shared entity — which also means one definition
 * of what "this account may authenticate" means, rather than four that could drift.
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final GamerRepository gamerRepository;

    /**
     * Records that a gamer used the app, for the re-engagement scheduler.
     *
     * <p>Declared here rather than annotated on the class so that {@code @WebMvcTest}
     * slices do not pick it up: they build a web context without repositories, and a
     * component-scanned filter needing one fails every controller test.
     */
    @Bean
    public FilterRegistrationBean<LastActiveTracker> lastActiveTracker(Clock clock) {
        FilterRegistrationBean<LastActiveTracker> registration =
                new FilterRegistrationBean<>(new LastActiveTracker(gamerRepository, clock));
        // Last in the chain: it reads the authenticated principal, so it has to run after
        // authentication has established one.
        registration.setOrder(Integer.MAX_VALUE);
        return registration;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Throws the Spring Security type rather than BusinessException so the
        // authentication machinery can translate it into a 401 itself.
        return email -> gamerRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }

    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder);
        // Emit BadCredentialsException for unknown users too, so the login endpoint
        // cannot be used to enumerate which email addresses are registered.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    /**
     * Injected rather than read through {@code Instant.now()}, so anything that depends on
     * time — subscription expiry, swipe windows, impression timestamps — can be tested
     * without waiting for a month to pass.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
