package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConversationResponseBody implements BaseModel {
    private List<ConversationDto> conversations;
}
