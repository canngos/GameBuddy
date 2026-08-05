package com.gamebuddy.community.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.community.interfaces.request.ReportRequest;
import com.gamebuddy.community.interfaces.response.ReportsResponse;
import com.gamebuddy.shared.entity.Gamer;
import org.springframework.data.domain.Pageable;

/**
 * Reporting and moderating community content.
 *
 * <p>Reporting existed only for chat messages. The moderation flow also stopped at
 * "clear the flag": there was no way to get from a report to the gamer who wrote the
 * content, so a moderator reading a report had to go and find them by hand, and nothing
 * recorded who had reported what.
 */
public interface ModerationService {

    DefaultMessageResponse reportPost(Gamer principal, String postId, ReportRequest request);

    DefaultMessageResponse reportComment(Gamer principal, String commentId, ReportRequest request);

    /**
     * Reports a gamer's profile.
     *
     * <p>The one report that is not about something written in a community. A profile
     * picture or a username can be the whole problem, and until this existed the only way
     * to raise one was to wait for the person to post something — or to send them a
     * message and report that, which asks the reporter to keep talking to somebody they
     * want reported.
     */
    DefaultMessageResponse reportProfile(Gamer principal, String userId, ReportRequest request);

    /** The moderation queue, oldest first. Admins only. */
    ReportsResponse getOpenReports(Gamer principal, Pageable pageable);

    /** Removes the reported content and closes every open report against it. */
    DefaultMessageResponse actionReport(Gamer principal, String reportId);

    /** Judges the content acceptable and closes the report. */
    DefaultMessageResponse dismissReport(Gamer principal, String reportId);
}
