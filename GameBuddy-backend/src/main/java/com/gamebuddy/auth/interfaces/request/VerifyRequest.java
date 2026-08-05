package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyRequest {

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email is not valid")
    private String email;

    @NotNull(message = "Verification code cannot be empty")
    @Min(value = 100000, message = "Verification code must be 6 digits")
    @Max(value = 999999, message = "Verification code must be 6 digits")
    private Integer verificationCode;
}
