package com.gamebuddy.match.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * What a client may say over the chat socket.
 *
 * <p>Deliberately narrow. The endpoint used to accept the {@code Message} entity itself,
 * so the client supplied its own {@code sender}, {@code senderName}, {@code date},
 * {@code status} and {@code isReported} — everything the server is supposed to decide.
 * Only the recipient and the text are the caller's to choose.
 */
@Getter
@Setter
public class ChatMessageRequest {

    @NotBlank(message = "Receiver is required")
    @Size(max = 255, message = "Receiver is not valid")
    private String receiver;

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;
}
