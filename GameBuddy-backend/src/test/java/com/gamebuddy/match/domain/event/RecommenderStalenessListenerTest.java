package com.gamebuddy.match.domain.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.ProfileChangedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommenderStalenessListenerTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    private final GamerRepository gamerRepository = mock(GamerRepository.class);
    private final RecommenderStalenessListener listener =
            new RecommenderStalenessListener(gamerRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("a profile change marks the gamer as one the artefact predates")
    void marksTheGamerStale() {
        Gamer gamer = new Gamer();
        gamer.setUserId("me");
        when(gamerRepository.findById("me")).thenReturn(Optional.of(gamer));

        listener.onProfileChanged(new ProfileChangedEvent("me"));

        assertEquals(NOW, gamer.getRecommenderProfileChangedAt());
        verify(gamerRepository).save(gamer);
    }

    @Test
    @DisplayName("an unknown gamer is ignored rather than failing the edit that raised the event")
    void ignoresAnUnknownGamer() {
        when(gamerRepository.findById("ghost")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> listener.onProfileChanged(new ProfileChangedEvent("ghost")));
        verify(gamerRepository, never()).save(any());
    }
}
