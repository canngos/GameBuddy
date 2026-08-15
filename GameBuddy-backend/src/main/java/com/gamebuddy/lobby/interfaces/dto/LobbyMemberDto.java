package com.gamebuddy.lobby.interfaces.dto;

import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One roster row — a team member, or a pending request in the owner's inbox. */
@Getter
@Setter
public class LobbyMemberDto {

    private String userId;
    private String username;
    private String avatar;
    private LobbyMemberStatus status;
    private Instant requestedAt;
}
