package com.gamebuddy.lobby.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.Getter;
import lombok.Setter;

/**
 * The message as stored — screened text, not what was typed — so the sender's client
 * renders the same words everybody else will see.
 */
@Getter
@Setter
public class LobbyMessageResponseBody implements BaseModel {

    private LobbyMessageDto message;
}
