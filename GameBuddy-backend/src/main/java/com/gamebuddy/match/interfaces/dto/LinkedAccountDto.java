package com.gamebuddy.match.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A verified external account, as shown on a deck card.
 *
 * <p>Its own class rather than the profile module's, following {@link GamesDto} — every
 * module owns the shape it puts on the wire. Sharing one would tie two feature modules
 * together for two fields, and they are not the same two fields: the profile's version also
 * carries the visibility setting, which is meaningful only on your own profile and has no
 * business being computed for a page of strangers.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LinkedAccountDto {

    /** {@code DISCORD}. */
    private String provider;

    /** The display name, or null when it did not survive screening. */
    private String handle;
}
