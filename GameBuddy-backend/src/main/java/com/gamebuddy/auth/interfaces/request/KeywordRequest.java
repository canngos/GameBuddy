package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeywordRequest {
    @NotBlank(message = "Keyword cannot be empty")
    private String keyword;

    @NotBlank(message = "Keyword description cannot be empty")
    private String description;
}
