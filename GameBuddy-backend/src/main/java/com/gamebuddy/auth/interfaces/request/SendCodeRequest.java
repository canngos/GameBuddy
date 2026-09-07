package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendCodeRequest {
    @NotBlank(message = "Email field cannot be empty")
    @Email(message = "Email is not valid")
    @Size(max = 255, message = "Email is not valid")
    private String email;

    @NotNull(message = "isRegister field cannot be empty")
    private Boolean isRegister;
}
