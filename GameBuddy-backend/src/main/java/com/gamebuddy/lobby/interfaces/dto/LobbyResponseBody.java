package com.gamebuddy.lobby.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LobbyResponseBody implements BaseModel {

    private LobbyDto lobby;

    /** The team: OWNER and ACCEPTED rows. */
    private List<LobbyMemberDto> members;

    /** PENDING requests — filled only when the caller is the owner, empty otherwise. */
    private List<LobbyMemberDto> pendingRequests;
}
