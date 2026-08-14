package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** A catalogue entry. Every bound matches the column on {@code keywords}. */
@Getter
@Setter
public class KeywordRequest {
    @NotBlank(message = "Keyword cannot be empty")
    @Size(max = 255, message = "Keyword cannot exceed 255 characters")
    private String keyword;

    @NotBlank(message = "Keyword description cannot be empty")
    @Size(max = 255, message = "Keyword description cannot exceed 255 characters")
    private String description;
}
