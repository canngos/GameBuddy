package com.gamebuddy.community.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Why a gamer is reporting a post or a comment. */
@Getter
@Setter
public class ReportRequest {

    @NotBlank(message = "A reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
