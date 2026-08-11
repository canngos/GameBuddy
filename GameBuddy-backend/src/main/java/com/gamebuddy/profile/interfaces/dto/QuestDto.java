package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One weekly quest, with this week's progress towards it. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QuestDto implements BaseModel {

    /** The enum name, which is what a claim is addressed to. */
    private String code;

    private String title;

    /** Capped at {@code target} by the server, so a progress bar needs no guard. */
    private int progress;

    private int target;
    private int reward;

    /** True once paid this week. */
    private boolean claimed;
}
