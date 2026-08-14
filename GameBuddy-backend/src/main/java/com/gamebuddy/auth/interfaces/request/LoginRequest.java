package com.gamebuddy.auth.interfaces.request;

import com.gamebuddy.auth.domain.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = "Username or email field cannot be empty")
    @Size(max = 255, message = "Username or email is not valid")
    private String usernameOrEmail;

    // Bounded even though the value is only ever compared, never stored: bcrypt hashes
    // whatever it is handed, so an unbounded field here is work anybody can ask for
    // without a password that could possibly be right.
    @NotBlank(message = "Password field cannot be empty")
    @Size(max = PasswordPolicy.MAX_LENGTH, message = "Password field is not valid")
    private String password;
}
