package com.gamebuddy.lobby.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LobbyMessagesResponseBody implements BaseModel {

    private List<LobbyMessageDto> messages;
}
