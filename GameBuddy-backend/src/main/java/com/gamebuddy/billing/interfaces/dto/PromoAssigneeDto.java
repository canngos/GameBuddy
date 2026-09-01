package com.gamebuddy.billing.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One account a code was addressed to, for the console's edit screen.
 *
 * <p>The address is here for the same reason it is on the banned list: two similar
 * usernames are only reliably told apart by it, and taking somebody off a gift list is
 * exactly the moment to be sure which of them you mean.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PromoAssigneeDto implements BaseModel {

    private String userId;
    private String username;
    private String email;

    /** Null if the code was never emailed to this account. */
    private Instant emailedAt;

    /** Whether they have used it. A recipient who has cannot be taken off the list. */
    private boolean redeemed;
}
