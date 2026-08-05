package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameResponseBody implements BaseModel {

    private GamesDto gameData;
}
