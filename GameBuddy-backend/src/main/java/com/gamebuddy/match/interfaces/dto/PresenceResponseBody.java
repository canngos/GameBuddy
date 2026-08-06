package com.gamebuddy.match.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * The body of a presence lookup.
 *
 * <p>Exists so the answer travels in the same {@code {status, body:{data}}} envelope as
 * every other endpoint. Returning the bare record instead was tried and broke the client:
 * it unwraps {@code body.data} on every response, so an unwrapped payload arrives as
 * undefined — a silent empty answer rather than an error.
 */
@Getter
@Setter
public class PresenceResponseBody implements BaseModel {
    private String userId;
    private boolean online;
    private Instant lastSeenAt;
}
