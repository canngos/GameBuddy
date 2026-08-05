package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A refreshed Firebase device token.
 *
 * <p>The token was captured once, at registration, and there was no way to update it
 * afterwards. Firebase rotates these — on reinstall, on app-data clear, on restore to a
 * new device, and periodically on its own — so every gamer's push notifications broke
 * permanently the first time theirs changed, with nothing to indicate it had happened.
 * The client should call this on every start.
 */
@Getter
@Setter
public class FcmTokenRequest {

    @NotBlank(message = "Token cannot be empty")
    @Size(max = 512, message = "Token is too long")
    private String fcmToken;
}
