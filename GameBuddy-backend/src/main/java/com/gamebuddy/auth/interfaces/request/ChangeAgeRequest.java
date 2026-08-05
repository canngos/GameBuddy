package com.gamebuddy.auth.interfaces.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeAgeRequest {
    // Minors are welcome, but never matched with adults: AgeBand splits the
    // population at 18 and every pairing decision — recommendation, accept and
    // each chat message — requires both gamers to be in the same band.
    @Min(value = 12, message = "Age must be at least 12 years old")
    @Max(value = 99, message = "Age must be at most 99 years old")
    private int age;
}
