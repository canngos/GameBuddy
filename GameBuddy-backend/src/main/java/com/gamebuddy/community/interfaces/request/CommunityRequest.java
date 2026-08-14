package com.gamebuddy.community.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityRequest {
    @NotBlank(message = "Community id is required")
    @Size(max = 255, message = "Community id is not valid")
    private String communityId;
}
