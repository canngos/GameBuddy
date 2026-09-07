package com.gamebuddy.billing.interfaces.request;

import com.gamebuddy.billing.infrastructure.entity.PromoCodeKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Creating a promotion code, and editing one afterwards.
 *
 * <p>One shape for both. The alternative was a create request and an update request that
 * differed by two fields, which is two validation rules to keep in step and one screen in
 * the console building whichever it happened to need.
 *
 * <p>What the two calls do differently is documented on the service: {@code code} is only
 * read when creating (it cannot change afterwards — it may already be in somebody's inbox),
 * {@code disabled} is only read when editing, and {@code validDays} is always counted from
 * now, so editing a code with three days left and leaving the field at thirty gives it
 * thirty from today rather than adding to what it had.
 *
 * <p>The bounds are deliberately generous and are here only to keep a typo from becoming a
 * disaster: an extra zero on the coin field is the realistic mistake, not a hostile one —
 * the endpoint is behind ADMIN.
 */
@Getter
@Setter
public class PromoCodeRequest {

    @NotNull(message = "Choose what the code gives: COIN or GOLD")
    private PromoCodeKind kind;

    /** Required for a COIN code, ignored otherwise. */
    @Min(value = 1, message = "A coin code has to be worth at least one coin")
    @Max(value = 100_000, message = "That is more coins than any code should carry")
    private Integer coinAmount;

    /** Required for a GOLD code, ignored otherwise. */
    @Min(value = 1, message = "A Gold code has to be worth at least a day")
    @Max(value = 3650, message = "Ten years of Gold is not a promotion, it is a mistake")
    private Integer goldDays;

    /**
     * The code itself. Left empty, one is generated.
     *
     * <p>Dashes are allowed on the way in and stripped, because a code written down as
     * WELCOME-2026 is the same code as WELCOME2026 to everybody except a string comparison.
     */
    @Pattern(regexp = "[A-Za-z0-9-]{4,32}", message = "A code is 4 to 32 letters, digits or dashes")
    private String code;

    @NotNull(message = "Say how long the code stays valid")
    @Min(value = 1, message = "A code has to be valid for at least a day")
    @Max(value = 3650, message = "That is longer than the code will be remembered")
    private Integer validDays;

    /** Null means unlimited. */
    @Min(value = 1, message = "A code has to allow at least one redemption")
    private Integer maxRedemptions;

    /**
     * Who the code is for. Empty makes it public — anybody who knows the string may
     * redeem it.
     *
     * <p>Capped at two hundred because the send is synchronous: the response says how many
     * messages actually left, and it can only say that by waiting for them. Larger
     * campaigns are sent a page at a time, which also means a relay that starts refusing
     * halfway is noticed after two hundred messages rather than two thousand.
     */
    @Size(max = 200, message = "Send to at most 200 people at a time")
    private List<String> assigneeIds;

    /** Whether to email the code to recipients who have not been sent it yet. */
    private boolean sendEmail;

    /** Editing only: switch the code off, or back on. */
    private boolean disabled;

    @Size(max = 200, message = "Keep the note under 200 characters")
    private String note;
}
