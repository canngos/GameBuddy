package com.gamebuddy.auth.interfaces.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetailsRequest {

    /**
     * A date of birth. The age is derived from it here, never sent by the client.
     *
     * <p>GameBuddy is 18+. {@code AgePolicy} decides whether this date is one an account
     * holder may have, and {@code AgeBand} still separates on majority afterwards as a
     * second line of defence against an account whose age is missing or wrong.
     */
    @NotNull(message = "Date of birth is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @NotBlank(message = "Country field cannot be empty")
    @Size(max = 255, message = "Country is not valid")
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
    @Size(max = 255, message = "Avatar is not valid")
    private String avatar;

    @Size(max = 1, message = "Gender must be a single character")
    private String gender;

    // The requirements specify at least 3 games and 5 keywords; the previous
    // annotations enforced 1 and 3, so under-specified profiles reached the matcher.
    // The upper bounds are not a product rule, they are the same guard as everywhere else:
    // an unbounded list becomes an unbounded IN clause, and an unbounded element becomes a
    // megabyte inside one.
    @NotNull(message = "Favourite games cannot be empty")
    @Size(min = 3, max = 100, message = "Select between 3 and 100 games")
    private List<@Size(max = 255, message = "Game is not valid") String> favoriteGames;

    @NotNull(message = "Keywords cannot be empty")
    @Size(min = 5, max = 100, message = "Select between 5 and 100 keywords")
    private List<@Size(max = 255, message = "Keyword is not valid") String> keywords;

    /**
     * What they play on: {@code Platform} names, at least one.
     *
     * <p>Asked at signup rather than left for later because it is one tap and it decides
     * whether the platform filter is worth having at all — a field most accounts never fill
     * in produces a filter that hides more people than it finds.
     *
     * <p>Accounts created before this existed have an empty set and keep working; see
     * {@code upgrade-2026-24-platforms.sql} for why they are not backfilled, and
     * {@code FeedFilters} for why an empty set is never filtered out.
     */
    @NotNull(message = "Platforms cannot be empty")
    @Size(min = 1, max = 16, message = "Select at least 1 platform")
    private List<@Size(max = 32, message = "Platform is not valid") String> platforms;
}
