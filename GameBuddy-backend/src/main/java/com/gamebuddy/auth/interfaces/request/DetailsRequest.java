package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetailsRequest {

    // Minors are welcome, but never matched with adults: AgeBand splits the
    // population at 18 and every pairing decision — recommendation, accept and
    // each chat message — requires both gamers to be in the same band.
    @NotNull(message = "Age field cannot be empty")
    @Min(value = 12, message = "Age must be at least 12")
    @Max(value = 99, message = "Age must be at most 99")
    private Integer age;

    @NotBlank(message = "Country field cannot be empty")
    private String country;

    /**
     * Optional since avatars became uploads.
     *
     * <p>Was required, and picking from a catalogue of eight was the last step before an
     * account could be used. Now a gamer either uploads a picture or keeps the coloured
     * monogram, and neither should stand between them and the app — an upload has to be
     * screened before anyone sees it anyway, so requiring one at signup would mean the
     * first thing a new account does is wait.
     *
     * <p>Still accepted, and still validated when present, so an existing client that
     * sends a catalogue id keeps working until the catalogue is retired.
     */
    private String avatar;

    @Size(max = 1, message = "Gender must be a single character")
    private String gender;

    // The requirements specify at least 3 games and 5 keywords; the previous
    // annotations enforced 1 and 3, so under-specified profiles reached the matcher.
    @NotNull(message = "Favourite games cannot be empty")
    @Size(min = 3, message = "Select at least 3 games")
    private List<String> favoriteGames;

    @NotNull(message = "Keywords cannot be empty")
    @Size(min = 5, message = "Select at least 5 keywords")
    private List<String> keywords;
}
