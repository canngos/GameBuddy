package com.gamebuddy.moderation.interfaces.dto;

import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Everything a moderator needs to decide a case, in one screen.
 *
 * <p>The summary, the reports that make it up (each with its reason, note and reporter
 * weight), the target's own standing and history, and — for message reports — the decrypted
 * conversation around what was reported. Assembling it is the audited read: this is the DTO
 * whose construction logs that a named moderator saw private text.
 */
@Getter
@Setter
public class CaseDetailDto {

    private CaseSummaryDto summary;

    /** The reported person, as they are now (not the snapshot — the snapshot is per report). */
    private String targetUsername;

    private String targetAvatarKey;
    private String targetAvatarStatus;
    private Instant targetJoinedAt;
    private boolean targetSuspended;
    private Instant targetSuspendedUntil;

    private List<ReportItemDto> reports;

    /** For message reports: the decrypted context, oldest first, deduplicated across reports. */
    private List<ReportedMessageContextDto> messageContext;

    /** What has already been done to this account, newest first. */
    private List<ModerationActionDto> history;

    /** One report inside the case. */
    @Getter
    @Setter
    public static class ReportItemDto {
        private String reportId;
        private String contentType;
        private String reasonCode;
        private String note;
        private String reporterId;
        /** The reporter's weight at the time this view was built, 0–1. */
        private String reporterWeight;

        private Instant createdAt;
        /** The frozen snapshot, as JSON, so the console can show what the reporter saw. */
        private String evidence;
    }

    /** One message of decrypted context, shared shape with the app's chat view. */
    @Getter
    @Setter
    public static class ReportedMessageContextDto {
        private String messageId;
        private String senderId;
        private String senderUsername;
        private String message;
        private Instant sentAt;
        private boolean reported;
    }

    /** One past action against the target. */
    @Getter
    @Setter
    public static class ModerationActionDto {
        private String action;
        private String reasonCode;
        private String note;
        private String actorId;
        private Instant createdAt;
        private Instant expiresAt;
    }
}
