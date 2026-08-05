package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The outcome of an accept.
 *
 * <p>Exists because {@code matched} was previously only expressible in the status
 * message — "It's a match!" versus "Gamer accepted". That made the single most
 * important moment in the product depend on a client string-matching English copy, so
 * rewording the message, or ever translating it, would silently stop the match
 * celebration from appearing.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AcceptResponseBody implements BaseModel {

    /** True when the other gamer had already accepted, so a conversation is now open. */
    private boolean matched;

    /** Human-readable outcome, still supplied for callers that just want to show it. */
    private String message;
}
