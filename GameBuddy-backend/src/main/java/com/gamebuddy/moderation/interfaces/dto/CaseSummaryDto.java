package com.gamebuddy.moderation.interfaces.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One case as it appears in the moderator's queue: enough to sort and triage on, not the
 * evidence itself. The evidence is a click away, on {@link CaseDetailDto}, because reading
 * a decrypted conversation is an audited act and should happen when a moderator opens the
 * case, not for every row of the list.
 */
@Getter
@Setter
public class CaseSummaryDto {
    private String caseId;
    private String targetId;
    private String targetUsername;
    private String status;
    private BigDecimal weightedScore;
    private int distinctReporters;
    private Instant openedAt;
    private long ageHours;
    private boolean overdue;
    private boolean autoHidden;
    /** How many times this account has been actioned before; a repeat is a different case. */
    private long priorSanctions;
}
