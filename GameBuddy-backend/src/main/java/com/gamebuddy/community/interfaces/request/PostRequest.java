package com.gamebuddy.community.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A new post in a community.
 *
 * <p>Bounded to the columns behind it, for the reason given on {@link CreateCommunityRequest}:
 * an unbounded field is a 500 anybody with a token can trigger.
 */
@Getter
@Setter
public class PostRequest {
    @NotBlank(message = "Community id is required")
    @Size(max = 255, message = "Community id is not valid")
    private String communityId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @Size(max = 4000, message = "Post cannot exceed 4000 characters")
    private String body;

    @Size(max = 255, message = "Picture reference cannot exceed 255 characters")
    private String picture;
}
