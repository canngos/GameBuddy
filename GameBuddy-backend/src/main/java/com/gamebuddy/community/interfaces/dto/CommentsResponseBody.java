package com.gamebuddy.community.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentsResponseBody implements BaseModel {

    private List<CommentDto> comments;
}
