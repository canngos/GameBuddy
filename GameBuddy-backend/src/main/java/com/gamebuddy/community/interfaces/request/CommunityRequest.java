package com.gamebuddy.community.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityRequest {
    @NotBlank(message = "Community id is required")
    private String communityId;
}
