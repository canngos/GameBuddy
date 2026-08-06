package com.gamebuddy.match.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Who the typing indicator is for.
 *
 * <p>Only the recipient. The sender is the socket's authenticated principal, never a field
 * the client fills in — the same rule as {@link ChatMessageRequest}, and for the same
 * reason: anything the client is allowed to name, the client is allowed to lie about.
 */
@Getter
@Setter
public class TypingRequest {

    @NotBlank(message = "Receiver is required")
    private String receiver;
}
