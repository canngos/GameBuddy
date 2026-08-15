package com.gamebuddy.lobby.interfaces.request;

import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Everything an owner decides when opening a lobby.
 *
 * <p>The size bounds match the column widths exactly, so validation refuses what Postgres
 * would refuse — the community fields once disagreed and the constraint violation escaped
 * as a 500.
 */
@Getter
@Setter
public class CreateLobbyRequest {

    @NotBlank
    private String gameId;

    @NotBlank
    @Size(max = 80)
    private String title;

    @Size(max = 500)
    private String description;

    /** Free text: mic, rank, in-game chat. Informational only. */
    @Size(max = 300)
    private String requirements;

    @NotNull
    private LobbyTone tone;

    /** Including the owner. */
    @NotNull
    @Min(2)
    @Max(5)
    private Integer maxPlayers;

    /** The planned start; "now" is simply the current time. */
    @NotNull
    private Instant startsAt;
}
