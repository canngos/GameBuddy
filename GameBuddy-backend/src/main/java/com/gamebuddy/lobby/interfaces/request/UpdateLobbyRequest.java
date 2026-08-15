package com.gamebuddy.lobby.interfaces.request;

import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * An owner's edit while the lobby is OPEN. Every field optional; null means unchanged.
 *
 * <p>{@code maxPlayers} may only shrink to the seats already filled — an edit is not a way
 * to kick somebody without saying so.
 */
@Getter
@Setter
public class UpdateLobbyRequest {

    @Size(max = 80)
    private String title;

    @Size(max = 500)
    private String description;

    @Size(max = 300)
    private String requirements;

    private LobbyTone tone;

    @Min(2)
    @Max(5)
    private Integer maxPlayers;

    private Instant startsAt;
}
