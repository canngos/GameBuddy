package com.gamebuddy.community.interfaces.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One report, as a moderator sees it. */
@Getter
@Setter
public class ReportDto {
    private String reportId;
    private String contentType;
    private String contentId;

    /** Who wrote it; the admin bans this gamer through auth-service if warranted. */
    private String authorId;

    private String authorUsername;
    private String reporterId;
    private String reason;
    private String status;
    private Instant createdAt;

    /** The reported text itself, or null when the content has already been removed. */
    private String content;

    /** How many open reports stand against this author across all their content. */
    private Long authorOpenReportCount;

    /**
     * True once this report has been open longer than the 24 hours the terms promise.
     *
     * <p>Computed on the server rather than in the console, so the deadline has one
     * definition. A commitment nobody can see the state of is a commitment that gets
     * missed quietly.
     */
    private Boolean overdue;

    /** Hours this report has been open, so the console can show the ones getting close. */
    private Long ageHours;
}
