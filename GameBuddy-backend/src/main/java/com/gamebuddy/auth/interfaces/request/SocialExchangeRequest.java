package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * The ticket the Discord callback handed back through the deep link.
 *
 * <p>Not the one that travelled through Discord: that one is already spent. See
 * {@code SocialLoginTicket} for why there are two.
 */
@Getter
@Setter
public class SocialExchangeRequest {

    @NotBlank
    private String ticket;

    /** Consulted only where the exchange would create an account. */
    private Boolean acceptedTerms;
}
