package com.gamebuddy.auth.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A session, plus the one fact the client cannot work out for itself.
 *
 * <p>Shaped like {@code LoginResponseBody} with {@code newAccount} added. The app needs it to
 * tell "welcome back" from "let's set you up": both answers land the same JWT, and after that
 * the difference is invisible — an account created a second ago and one that never finished
 * onboarding are the same state on the wire.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SocialSessionResponseBody implements BaseModel {

    private String accessToken;
    private String userId;

    /** True only when this call created the account. */
    private boolean newAccount;
}
