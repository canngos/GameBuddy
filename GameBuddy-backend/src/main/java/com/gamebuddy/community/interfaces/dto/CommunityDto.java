package com.gamebuddy.community.interfaces.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityDto {

    private String communityId;
    private String name;
    private String description;
    private String communityAvatar;
    private String wallpaper;
    private Instant createdDate;
    private Integer memberCount;
    private Integer postCount;
    private Boolean isJoined;
}
