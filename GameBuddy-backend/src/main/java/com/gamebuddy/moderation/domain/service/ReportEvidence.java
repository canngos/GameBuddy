package com.gamebuddy.moderation.domain.service;

import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.Games;
import com.gamebuddy.shared.entity.Keywords;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/**
 * What the reporter was looking at, frozen.
 *
 * <p>A report that only records ids shows the moderator whatever is there <em>now</em>,
 * and the person reported has every reason to make "now" look different from "then". So
 * the profile is copied as text at the moment of the report, and the message report
 * records which messages surrounded it.
 *
 * <p>Messages are captured as ids, not text. Bodies are encrypted at rest and the one
 * place they are decrypted for somebody who was not in the conversation is
 * {@code ChatModerationService}, where the read is logged against the moderator. Copying
 * plaintext into this column would quietly create a second, unaudited place to read
 * private conversations from. The ids are enough: a message, once sent, is not edited.
 *
 * @param kind PROFILE or MESSAGE
 * @param capturedAt when the snapshot was taken
 * @param username the reported gamer's name at the time (profile)
 * @param avatarKey the object key of their picture at the time, or null (profile)
 * @param avatarStatus its review status at the time (profile)
 * @param country as shown on the profile (profile)
 * @param keywords play-style keywords at the time (profile)
 * @param games game names at the time (profile)
 * @param messageId the reported message (message)
 * @param roomId its conversation (message)
 * @param contextIds the reported message and the ones either side, in order (message)
 */
public record ReportEvidence(
        String kind,
        Instant capturedAt,
        String username,
        String avatarKey,
        String avatarStatus,
        String country,
        List<String> keywords,
        List<String> games,
        UUID messageId,
        UUID roomId,
        List<UUID> contextIds) {

    public static ReportEvidence ofProfile(Gamer target, Instant now) {
        return new ReportEvidence(
                "PROFILE",
                now,
                target.getGamerUsername(),
                target.getAvatarKey(),
                target.getAvatarStatus() == null
                        ? null
                        : target.getAvatarStatus().name(),
                target.getCountry(),
                target.getKeywords().stream()
                        .map(Keywords::getKeywordName)
                        .sorted()
                        .toList(),
                target.getLikedgames().stream().map(Games::getGameName).sorted().toList(),
                null,
                null,
                null);
    }

    public static ReportEvidence ofMessage(UUID messageId, UUID roomId, List<UUID> contextIds, Instant now) {
        return new ReportEvidence("MESSAGE", now, null, null, null, null, null, null, messageId, roomId, contextIds);
    }

    public String toJson(ObjectMapper json) {
        return json.writeValueAsString(this);
    }

    /** Null-safe: rows filed before evidence existed have none, and read as such. */
    public static ReportEvidence fromJson(ObjectMapper json, String stored) {
        return stored == null ? null : json.readValue(stored, ReportEvidence.class);
    }
}
