package com.gamebuddy.admin.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ModeratorBootstrapTest {

    private static final String PASSWORD = "auV8Z-DzP6UC1Non4UOGrV";

    private final GamerRepository gamerRepository = mock(GamerRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private ModeratorBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new ModeratorBootstrap(gamerRepository, passwordEncoder);
        configure("admin@example.com", PASSWORD);
    }

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(bootstrap, "email", email);
        ReflectionTestUtils.setField(bootstrap, "password", password);
        ReflectionTestUtils.setField(bootstrap, "username", "moderator");
    }

    @Test
    @DisplayName("creates one ADMIN account, hashed, registered, and with no profile")
    void createsTheModerator() {
        when(gamerRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

        bootstrap.run(null);

        ArgumentCaptor<Gamer> captor = ArgumentCaptor.forClass(Gamer.class);
        verify(gamerRepository).save(captor.capture());
        Gamer moderator = captor.getValue();

        assertEquals(Role.ADMIN, moderator.getRole());
        assertEquals("hashed", moderator.getPwd(), "the plaintext password must never be stored");
        assertTrue(moderator.getIsVerified());
        assertTrue(moderator.getIsRegistered(), "otherwise login refuses it as a half-finished account");
        assertNull(moderator.getAge(), "the moderator has no profile and never completes onboarding");
        assertTrue(moderator.getLikedgames().isEmpty());
        assertFalse(moderator.isDiscoverable(), "a staff account must never enter the population");
    }

    @Test
    @DisplayName("the address is lower-cased, so a capitalised variable cannot create a second account")
    void normalisesTheAddress() {
        configure("  Admin@Example.COM ", PASSWORD);
        when(gamerRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        bootstrap.run(null);

        verify(gamerRepository).findByEmail("admin@example.com");
    }

    @Test
    @DisplayName("an existing account is never promoted or re-passworded")
    void neverTouchesAnExistingAccount() {
        Gamer existing = new Gamer();
        existing.setRole(Role.USER);
        when(gamerRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

        bootstrap.run(null);

        verify(gamerRepository, never()).save(any());
        assertEquals(Role.USER, existing.getRole(), "an environment variable must not escalate a user to admin");
    }

    @Test
    @DisplayName("no credentials configured means no staff account, not a broken start")
    void doesNothingWithoutCredentials() {
        configure("", "");

        assertDoesNotThrow(() -> bootstrap.run(null));
        verify(gamerRepository, never()).findByEmail(any());
        verify(gamerRepository, never()).save(any());
    }

    @Test
    @DisplayName("a weak password is refused rather than quietly accepted for the most privileged account")
    void refusesAWeakPassword() {
        configure("admin@example.com", "password");
        when(gamerRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> bootstrap.run(null));
        verify(gamerRepository, never()).save(any());
    }
}
