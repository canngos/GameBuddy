package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeDetailRequest {
    /**
     * Game or keyword ids, depending on the endpoint.
     *
     * <p>Both bounds matter and for different reasons. The list bound stops an
     * {@code IN (...)} clause the length of the request body; the element bound stops one
     * entry of it being megabytes long. Neither could happen through the app — the pickers
     * offer a catalogue — which is exactly why nothing enforced them.
     */
    @NotEmpty
    @Size(max = 100, message = "Too many selections")
    private List<@Size(max = 255, message = "Selection is not valid") String> gamesOrKeywordsList;
}
