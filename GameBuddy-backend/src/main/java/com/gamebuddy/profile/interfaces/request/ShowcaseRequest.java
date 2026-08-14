package com.gamebuddy.profile.interfaces.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * The badges to put on show, in the order they should appear.
 *
 * <p>An empty list is valid and means "show nothing", which is how the last badge comes
 * off. The size cap is checked here as well as in the service: this one gives a clean
 * validation error, the other is what actually holds when something calls the service
 * directly.
 */
@Getter
@Setter
public class ShowcaseRequest {

    @NotNull
    @Size(max = 3, message = "You can show at most three badges")
    private List<@Size(max = 64, message = "Badge code is not valid") String> codes;
}
