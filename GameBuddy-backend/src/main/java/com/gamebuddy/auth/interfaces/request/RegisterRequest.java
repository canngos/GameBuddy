package com.gamebuddy.auth.interfaces.request;

import com.gamebuddy.auth.domain.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @Email(message = "Email is not valid")
    @NotBlank(message = "Email field cannot be empty")
    private String email;

    // Length is checked here for a fast 400; PasswordPolicy applies the full rules.
    @NotBlank(message = "Password field cannot be empty")
    @Size(
            min = PasswordPolicy.MIN_LENGTH,
            max = PasswordPolicy.MAX_LENGTH,
            message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "Firebase token field cannot be empty")
    private String fcmToken;
}
