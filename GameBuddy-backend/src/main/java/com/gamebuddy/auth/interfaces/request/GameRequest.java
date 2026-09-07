package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** A catalogue entry. Every bound matches the column on {@code games}. */
@Getter
@Setter
public class GameRequest {
    @NotBlank(message = "Game name cannot be empty")
    @Size(max = 255, message = "Game name cannot exceed 255 characters")
    private String gameName;

    @NotBlank(message = "Game description cannot be empty")
    @Size(max = 255, message = "Game description cannot exceed 255 characters")
    private String gameDescription;

    @Size(max = 255, message = "Game icon reference cannot exceed 255 characters")
    private String gameIcon;

    @NotBlank(message = "Game category cannot be empty")
    @Size(max = 255, message = "Game category cannot exceed 255 characters")
    private String category;

    @NotNull(message = "Game rating cannot be empty")
    private Float rating;
}
