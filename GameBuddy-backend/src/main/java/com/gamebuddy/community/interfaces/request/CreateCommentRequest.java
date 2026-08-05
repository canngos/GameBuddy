package com.gamebuddy.community.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommentRequest {

    @NotBlank(message = "Post id is required")
    private String postId;

    @NotBlank(message = "Comment cannot be empty")
    @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
    private String message;
}
