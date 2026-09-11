package com.gamebuddy.moderation.interfaces.request;

import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Why a gamer is being reported.
 *
 * <p>The reason is now a code, not the text of a button. A fixed set means the same thing in
 * every language and lets the policy act on it — an UNDERAGE report is urgent whoever files
 * it, a SEXUAL report about a picture pulls the picture. The reporter may add a note; it is
 * required only when the reason is OTHER, since "other" with nothing written is not something
 * anyone can act on.
 *
 * <p>{@code reasonCode} is not marked {@code @NotNull}: a build shipped before this change
 * sends the old free-text {@code reason} instead, and refusing it outright would break
 * reporting for everyone who has not updated yet. The service treats a missing code as OTHER
 * and keeps the text. New builds always send a code. This tolerance is removed once the old
 * builds are gone.
 */
@Getter
@Setter
public class ReportRequest {

    private ContentReport.ReasonCode reasonCode;

    @Size(max = 300, message = "A note cannot exceed 300 characters")
    private String note;

    /** Legacy: the free-text reason older builds send. Kept for one OTA cycle. */
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
