package com.gamebuddy.lobby.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LobbyMessageRequest {

    @NotBlank
    @Size(max = 1000)
    private String message;
}
