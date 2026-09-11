package com.gamebuddy.match.interfaces.dto;

import java.util.List;
import java.util.UUID;

/**
 * A message that is about to be reported, described for the report.
 *
 * <p>Built by chat for the moderation module, which files the report. Chat knows who was
 * in the room and what was said around the message; moderation knows what to do with a
 * complaint. Handing over ids rather than text keeps every decryption on the one audited
 * path.
 *
 * @param messageId the reported message
 * @param roomId its conversation
 * @param senderId who wrote it — the person the report is about
 * @param contextIds the messages around it, oldest first, the reported one included
 */
public record MessageForReport(UUID messageId, UUID roomId, String senderId, List<UUID> contextIds) {}
