package com.gamebuddy.match.domain.service.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored message, in the form the socket needs to deliver it.
 *
 * <p>Carries the plaintext body rather than the entity. The stored body is ciphertext, so
 * handing the entity to the controller would mean either shipping unreadable bytes to the
 * client or decrypting somewhere further out — and the further the plaintext travels, the
 * more places it can be logged by accident.
 *
 * @param id the message
 * @param senderId who sent it
 * @param senderName their display name, for the notification
 * @param body the decrypted text
 * @param sentAt when it was stored
 * @param receiverPrincipalName the recipient's email; {@code convertAndSendToUser} routes
 *     by {@code Principal#getName}, which is the email, while messages carry user ids
 */
public record SentMessage(
        UUID id, String senderId, String senderName, String body, Instant sentAt, String receiverPrincipalName) {}
