package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Step one of a password reset: the address and the code that was mailed to it.
 *
 * <p>Shaped exactly like {@link VerifyRequest}, and separate from it on purpose. The two
 * codes are not interchangeable — a signup code cannot reset a password and a reset code
 * cannot verify an account — so the endpoints that redeem them do not share a request type
 * that would suggest otherwise.
 */
@Getter
@Setter
public class ResetVerifyRequest {

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email is not valid")
    @Size(max = 255, message = "Email is not valid")
    private String email;

    @NotNull(message = "Verification code cannot be empty")
    @Min(value = 100000, message = "Verification code must be 6 digits")
    @Max(value = 999999, message = "Verification code must be 6 digits")
    private Integer verificationCode;
}
