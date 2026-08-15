package com.gamebuddy.lobby.interfaces.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LobbyMessageDto {

    private String id;
    private String senderId;
    private String senderUsername;
    private String message;
    private Instant date;
}
