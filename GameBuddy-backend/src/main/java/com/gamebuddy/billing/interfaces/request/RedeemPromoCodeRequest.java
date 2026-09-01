package com.gamebuddy.billing.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** A code somebody typed, or tapped on their own promotion codes screen. */
@Getter
@Setter
public class RedeemPromoCodeRequest {

    /**
     * Normalised server-side — trimmed, uppercased, dashes and spaces removed — so a code
     * read off a printed card works however it was punctuated.
     */
    @NotBlank(message = "Enter a code")
    @Size(max = 40, message = "That is longer than any code")
    private String code;
}
