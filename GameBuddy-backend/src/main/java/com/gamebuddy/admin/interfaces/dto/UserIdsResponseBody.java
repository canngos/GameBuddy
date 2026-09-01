package com.gamebuddy.admin.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Everybody matching a search, as ids — what "select all of them" resolves to.
 *
 * <p>{@code truncated} is the honest half of that offer. The cap is the same two hundred a
 * single send is limited to, and a screen that quietly selected the first two hundred of
 * six hundred would have an administrator believing a campaign went out that mostly did
 * not.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserIdsResponseBody implements BaseModel {

    private List<String> ids;

    /** True when more accounts matched than the cap allowed. */
    private boolean truncated;
}
