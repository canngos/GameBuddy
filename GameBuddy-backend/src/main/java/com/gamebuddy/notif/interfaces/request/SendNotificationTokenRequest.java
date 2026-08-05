package com.gamebuddy.notif.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SendNotificationTokenRequest {
    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Body is required")
    private String body;

    private String imageUrl;

    /**
     * Sent as FCM data rather than in the visible notification: what it is about, and
     * what to open. The client reads these when the notification is tapped.
     */
    private String kind;

    private String targetId;
}
