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

    /**
     * Ignored, and optional on purpose.
     *
     * <p>It was {@code @NotBlank}, which forced a client that cannot yet have a device
     * token to invent one — and the placeholder it sent was then stored for every account,
     * making the token column non-unique. See the comment in {@code DefaultAuthService}
     * where it used to be persisted. The field stays so that an already-installed client
     * still validates; a device is registered after sign-in, through
     * {@code PUT /auth/fcm-token}.
     */
    private String fcmToken;
}
