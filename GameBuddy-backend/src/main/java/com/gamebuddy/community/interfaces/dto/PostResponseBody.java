package com.gamebuddy.community.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PostResponseBody implements BaseModel {

    private List<PostDto> posts;
}
