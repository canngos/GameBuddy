package com.gamebuddy.auth.interfaces.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeAgeRequest {

    /**
     * A date of birth, not an age.
     *
     * <p>This endpoint used to take an integer and store it unchecked, which meant the
     * boundary the whole safety model rested on could be crossed by typing a different
     * number into a settings field. GameBuddy is 18+ now, so that particular crossing no
     * longer exists — but the value is still re-checked here rather than trusted, and the
     * change is logged.
     */
    @NotNull(message = "Date of birth is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
}
