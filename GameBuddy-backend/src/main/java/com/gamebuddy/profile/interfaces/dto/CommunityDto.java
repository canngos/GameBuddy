package com.gamebuddy.profile.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CommunityDto {

    private String communityId;
    private String name;
    private String communityAvatar;
    private Boolean isOwner;
}
