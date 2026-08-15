package com.gamebuddy.moderation.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.moderation.interfaces.request.ReportRequest;
import com.gamebuddy.moderation.interfaces.response.ReportsResponse;
import com.gamebuddy.shared.entity.Gamer;
import org.springframework.data.domain.Pageable;

/**
 * Profile reports, and the moderator queue that answers them.
 *
 * <p>Lived in the community module while posts and comments were reportable; those
 * surfaces retired with Communities, and what remained — reporting a person, and the one
 * queue a moderator checks — is its own concern now. Historical POST/COMMENT reports are
 * still readable in the queue; new ones cannot be filed.
 */
public interface ModerationService {

    /**
     * Reports a gamer's profile.
     *
     * <p>The report that is not about anything written anywhere. A profile picture or a
     * username can be the whole problem, and until this existed the only way to raise one
     * was to send the person a message and report that — which asks the reporter to keep
     * talking to somebody they want reported.
     */
    DefaultMessageResponse reportProfile(Gamer principal, String userId, ReportRequest request);

    /** The moderation queue, oldest first. Admins only. */
    ReportsResponse getOpenReports(Gamer principal, Pageable pageable);

    /** Upholds the report and closes every open report against the same content. */
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
