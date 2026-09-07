package com.gamebuddy.profile.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FriendRequest {
    @NotBlank(message = "User ID cannot be empty")
    @Size(max = 255, message = "User ID is not valid")
    private String userId;
}
