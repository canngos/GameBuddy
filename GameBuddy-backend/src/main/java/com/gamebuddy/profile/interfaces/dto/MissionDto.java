package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One dealt mission, with progress towards it since it was dealt. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MissionDto implements BaseModel {

    /**
     * The mission's stable kebab-case code, which is both what a claim is addressed to and
     * the key the app translates the title by.
     */
    private String code;

    /**
     * English, and a fallback rather than the thing anybody reads.
     *
     * <p>The app looks the title up by {@link #code} in its own dictionaries and falls back
     * to this. That is what lets a mission be added server-side without six languages
     * rendering blank until the next store release — it renders in English instead.
     */
    private String title;

    /** 0, 1 or 2. Sent so the three keep a stable order rather than a list-order one. */
    private int slot;

    /** Capped at {@code target} by the server, so a progress bar needs no guard. */
    private int progress;

    private int target;

    /** Frozen when this was dealt, so retuning the rates cannot move it under the gamer. */
    private int reward;

    /** True once paid. When all three are true a new set has already replaced them. */
    private boolean claimed;
}
