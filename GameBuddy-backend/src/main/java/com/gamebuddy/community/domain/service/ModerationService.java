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

    /**
     * How many reports are still waiting to be looked at.
     *
     * <p>Here rather than letting the console query {@code content_report} itself: reports
     * belong to this module, and a repository is module-private. A plain {@code long} so
     * the caller cannot accidentally acquire the reports themselves along with the count.
     */
    long openReportCount();

    /**
     * How long the oldest open report has been waiting, in hours; zero when nothing is
     * waiting. The single number that says whether the 24-hour commitment is being kept.
     */
    long oldestOpenReportHours();
}
