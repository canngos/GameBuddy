package com.gamebuddy.auth.interfaces.request;

import com.gamebuddy.auth.domain.service.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Step two: the ticket earned in step one, and the password to set.
 *
 * <p>No current password, which is the whole point of the flow — the caller has proved
 * control of the mailbox instead. The length bound mirrors {@link ChangePwdRequest} so a
 * hopeless password is a cheap 400 rather than a bcrypt round; the real policy is applied
 * in the service.
 */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email is not valid")
    @Size(max = 255, message = "Email is not valid")
    private String email;

    @NotBlank(message = "Reset token cannot be empty")
    @Size(max = 128, message = "Reset token is not valid")
    private String resetToken;

    @NotBlank(message = "New password cannot be empty")
    @Size(
            min = PasswordPolicy.MIN_LENGTH,
            max = PasswordPolicy.MAX_LENGTH,
            message = "Password must be between 8 and 128 characters")
    private String password;
}
