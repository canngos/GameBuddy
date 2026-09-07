package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * A first password, for an account that signed up with Google or Discord.
 *
 * <p>No current password, because there is none. What authorises this is the session itself:
 * adding a way in is not a destructive act, and somebody holding a valid token for the
 * account is already able to do everything a password would let them do.
 */
@Getter
@Setter
public class SetPasswordRequest {

    @NotBlank
    private String password;
}
