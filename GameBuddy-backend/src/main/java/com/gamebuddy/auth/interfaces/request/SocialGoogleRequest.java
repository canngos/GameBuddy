package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * A Google ID token from the device's account sheet.
 *
 * <p>{@code acceptedTerms} is not required, and that is deliberate rather than lax. Most
 * calls here are a returning gamer who agreed long ago and must not be asked again; the flag
 * is consulted only where this call would create an account, and the server refuses with
 * {@code TERMS_NOT_ACCEPTED} so the app can show the consent sheet and retry the same token.
 */
@Getter
@Setter
public class SocialGoogleRequest {

    @NotBlank
    private String idToken;

    private Boolean acceptedTerms;
}
