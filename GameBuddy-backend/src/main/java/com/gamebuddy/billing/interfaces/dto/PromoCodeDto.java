package com.gamebuddy.billing.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One promotion code, as the console shows it.
 *
 * <p>{@code status} is sent rather than left to the client to work out from the expiry, the
 * counter and the disabled flag. Three fields and a clock is a rule, and a rule written on
 * both sides is a rule that eventually differs on one of them — the console would be the
 * side that got it wrong, because it is the side without the clock the codes are judged
 * against.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromoCodeDto implements BaseModel {

    private String id;
    private String code;

    /** COIN or GOLD. */
    private String kind;

    /** Set for COIN codes. */
    private Integer coinAmount;

    /** Set for GOLD codes. */
    private Integer goldDays;

    private Instant expiresAt;

    /** Null means unlimited. */
    private Integer maxRedemptions;

    private int redemptionCount;

    /** How many accounts the code was addressed to. Zero means it is public. */
    private int assigneeCount;

    /** How many of those have been emailed the code. */
    private int emailedCount;

    private Instant disabledAt;

    /** ACTIVE, EXPIRED, EXHAUSTED or DISABLED, worked out against the server's clock. */
    private String status;

    private String note;
    private Instant createdAt;

    /**
     * Who the code was addressed to. Only filled in when one code was asked for by id —
     * the list screen shows counts, and two hundred recipients per row would make the
     * response larger than everything else on the screen put together.
     */
    private List<PromoAssigneeDto> assignees;

    /** How the send went, on the response to a create or an edit that asked for one. */
    private EmailOutcome emailed;

    /**
     * @param sent how many messages left
     * @param failed how many the relay refused; the code still exists and still works
     */
    public record EmailOutcome(int sent, int failed) {}
}
