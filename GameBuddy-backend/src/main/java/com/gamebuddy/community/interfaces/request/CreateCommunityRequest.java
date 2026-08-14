package com.gamebuddy.community.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A new community.
 *
 * <p>Every bound here matches the column behind it. Without them the value passed
 * validation, reached Postgres, and the constraint violation escaped as a 500 — so any
 * authenticated caller could produce server errors on demand by typing enough characters.
 * {@link CreateCommentRequest} always had this right and is the template.
 */
@Getter
@Setter
public class CreateCommunityRequest {
    @NotBlank(message = "Title of the community is required")
    @Size(max = 255, message = "Community name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "Description of the community is required")
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @Size(max = 255, message = "Avatar reference cannot exceed 255 characters")
    private String avatar;

    @Size(max = 255, message = "Wallpaper reference cannot exceed 255 characters")
    private String wallpaper;
}
