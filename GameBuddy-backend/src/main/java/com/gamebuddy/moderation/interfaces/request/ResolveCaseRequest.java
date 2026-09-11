package com.gamebuddy.moderation.interfaces.request;

import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import com.gamebuddy.moderation.infrastructure.entity.ModerationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A moderator's one decision about a case.
 *
 * <p>One outcome, chosen from the ladder, optionally with the picture removed alongside it.
 * The reason is the rule the moderator judges was broken — it need not match what any single
 * reporter picked. A note is free text; the service requires one only for a ban.
 */
@Getter
@Setter
public class ResolveCaseRequest {

    @NotNull(message = "An action is required")
    private ModerationAction.Action action;

    private ContentReport.ReasonCode reasonCode;

    @Size(max = 500, message = "A note cannot exceed 500 characters")
    private String note;

    /** Take the picture down as part of this decision, without making it a separate visit. */
    private boolean removePhoto;
}
