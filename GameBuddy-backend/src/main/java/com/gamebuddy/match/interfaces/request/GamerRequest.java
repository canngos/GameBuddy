package com.gamebuddy.match.interfaces.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GamerRequest {
    @NotBlank(message = "userId field cannot be empty")
    @Size(max = 255, message = "userId is not valid")
    private String userId;

    /**
     * Spend a super like on this one, if any are owned.
     *
     * <p>Absent and false mean the same thing, so an older client keeps working and never
     * accidentally spends an inventory it does not know about. A request that asks for one
     * without owning one is refused rather than downgraded to an ordinary like: the gamer
     * meant to make a statement, and quietly not making it is worse than saying no.
     */
    private boolean superLike;
}
