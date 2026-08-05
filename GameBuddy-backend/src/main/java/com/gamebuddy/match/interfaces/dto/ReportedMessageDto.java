package com.gamebuddy.match.interfaces.dto;

import java.time.Instant;

/**
 * One reported message, as a moderator sees it.
 *
 * <p>Carries the decrypted body. This is the only path in the application that hands
 * plaintext to anyone other than the two people in the conversation, which is exactly why
 * the service that builds it logs every call.
 *
 * @param id the message
 * @param roomId the conversation it belongs to
 * @param senderId who wrote it
 * @param senderUsername their display name, resolved for the moderation screen
 * @param message the decrypted text
 * @param sentAt when it was sent
 * @param reportedAt when it was reported
 */
public record ReportedMessageDto(
        String id,
        String roomId,
        String senderId,
        String senderUsername,
        String message,
        Instant sentAt,
        Instant reportedAt) {}
