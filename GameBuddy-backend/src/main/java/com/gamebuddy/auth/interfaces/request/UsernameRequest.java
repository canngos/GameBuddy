package com.gamebuddy.auth.interfaces.request;

import com.gamebuddy.auth.domain.service.UsernamePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsernameRequest {

    // The real rules live in UsernamePolicy, which reports precisely what is wrong. This
    // only stops an absurd value reaching it, and shares the policy's own bound so the two
    // cannot drift.
    @NotBlank(message = "Username cannot be empty")
    @Size(max = UsernamePolicy.MAX_LENGTH, message = "Username is too long")
    private String username;
}
