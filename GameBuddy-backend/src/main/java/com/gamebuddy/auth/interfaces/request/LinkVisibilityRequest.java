package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** {@code PUBLIC} or {@code MATCHES}; anything else is refused. */
@Getter
@Setter
public class LinkVisibilityRequest {

    @NotBlank
    private String visibility;
}
