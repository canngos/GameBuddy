package com.gamebuddy.match.interfaces.dto;

/**
 * What the recipient's socket receives when a message arrives.
 *
 * <p>A wire payload, not a stored row — it used to sit in {@code infrastructure.entity}
 * alongside the JPA classes, which invited the assumption that it was persisted.
 *
 * <p>{@code message} is plaintext: it has just been decrypted for delivery. It is never
 * written anywhere in this form.
 *
 * @param id the message
 * @param senderId who sent it
 * @param senderName their display name
 * @param message the text
 */
public record ChatNotification(String id, String senderId, String senderName, String message) {}
