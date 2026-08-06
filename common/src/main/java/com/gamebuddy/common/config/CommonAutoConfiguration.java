package com.gamebuddy.common.config;

import com.gamebuddy.common.exception.GlobalExceptionHandler;
import com.gamebuddy.common.observability.RequestLoggingFilter;
import com.gamebuddy.common.security.JwtAuthenticationFilter;
import com.gamebuddy.common.security.JwtProperties;
import com.gamebuddy.common.security.JwtService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wires the shared infrastructure into every service.
 *
 * <p>Registered through {@code AutoConfiguration.imports} rather than component
 * scanning, because each service only scans its own {@code com.gamebuddy.<service>}
 * package. This replaces the {@code ApplicationConfig} / {@code ControllerExceptionHandler}
 * pair that was copy-pasted into all five services.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * The access log and the per-request MDC context.
     *
     * <p>Registered explicitly rather than as a plain {@code @Bean} of a {@code Filter}
     * type so the order can be set: it has to wrap the Spring Security chain, or a request
     * rejected with 401 would never be logged at all — and an endpoint quietly returning
     * 401 to everybody is precisely the sort of thing the logs are for.
     */
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(RequestLoggingFilter.ORDER);
        return registration;
    }

    /** Each service supplies its own {@link UserDetailsService} over its own Gamer entity. */
    @Bean
    @ConditionalOnBean(UserDetailsService.class)
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService, UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }
}
