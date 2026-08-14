package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeAvatarRequest {
    @NotBlank(message = "Avatar ID cannot be empty")
    @Size(max = 255, message = "Avatar ID is not valid")
    private String avatarId;
}
