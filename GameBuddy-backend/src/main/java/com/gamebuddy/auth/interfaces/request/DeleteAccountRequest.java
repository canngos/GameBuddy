package com.gamebuddy.auth.interfaces.request;

import com.gamebuddy.auth.domain.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Confirms an account deletion.
 *
 * <p>The password is required for the same reason it is required to change one: a stolen
 * token should not be enough to destroy the account it was stolen from.
 */
@Getter
@Setter
public class DeleteAccountRequest {

    @NotBlank(message = "Your current password is required to delete your account")
    @Size(max = PasswordPolicy.MAX_LENGTH, message = "Password is not valid")
    private String currentPassword;
}
