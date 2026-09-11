package com.gamebuddy.moderation.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.moderation.interfaces.request.ReportRequest;
import com.gamebuddy.moderation.interfaces.request.ResolveCaseRequest;
import com.gamebuddy.moderation.interfaces.response.CaseDetailResponse;
import com.gamebuddy.moderation.interfaces.response.CasesResponse;
import com.gamebuddy.shared.entity.Gamer;
import java.util.Set;
import java.util.UUID;

/**
 * Reports, the cases they build, and the decisions that close them.
 *
 * <p>A report is evidence about a person; a case is every open report about that person,
 * decided once. Filing is open to any signed-in gamer; everything from {@link #getCases}
 * down is for moderators.
 */
public interface ModerationService {

    /** Reports a gamer's profile — the picture, the name, what they wrote about themselves. */
    DefaultMessageResponse reportProfile(Gamer principal, String userId, ReportRequest request);

    /**
     * Reports a chat message. Only the recipient may; the sender and anyone outside the
     * conversation are refused. The message and the ten either side are captured as context.
     */
    DefaultMessageResponse reportMessage(Gamer principal, UUID messageId, ReportRequest request);

    /** The open and urgent cases, urgent first then oldest first. Admins only. */
    CasesResponse getCases(Gamer principal, int limit);

    /** One case with its evidence, its target's history, and the decrypted context. Admins only. */
    CaseDetailResponse getCase(Gamer principal, String caseId);

    /** Resolves a case with one decision from the ladder. Admins only. */
    DefaultMessageResponse resolveCase(Gamer principal, String caseId, ResolveCaseRequest request);

    // --- Read by the admin module's analytics and directory ----------------

    /** How many cases are still waiting for a moderator. */
    long openReportCount();

    /** How long the oldest open case has waited, in hours; zero when the queue is clear. */
    long oldestOpenReportHours();

    /** Everybody whose report a moderator upheld — the directory's "worth thanking" filter. */
    Set<String> reporterIdsWithActionedReports();
}
