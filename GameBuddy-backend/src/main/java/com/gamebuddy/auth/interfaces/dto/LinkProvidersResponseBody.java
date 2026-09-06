package com.gamebuddy.auth.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Which providers this deployment can actually link.
 *
 * <p>Configuration, not a property of the account — but the app has no other way to know it.
 * Credentials are per-deployment and optional by design, so a build that ships with both
 * providers in the UI would offer somebody a button that can only ever fail, which
 * reads as a broken app rather than an unconfigured one.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LinkProvidersResponseBody implements BaseModel {

    /** Enum names — {@code DISCORD}. Possibly empty. */
    private List<String> providers;
}
