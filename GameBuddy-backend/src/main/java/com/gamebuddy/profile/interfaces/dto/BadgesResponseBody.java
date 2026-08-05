package com.gamebuddy.profile.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * The whole board.
 *
 * <p>Every mission, earned or not, in catalogue order — the locked ones are the point of
 * the screen, so filtering them out server-side would leave the client with a list of
 * things already done and nothing to aim at.
 *
 * <p>Collecting and re-showcasing both return this same body, for the reason the store
 * does: after a claim the balance, that badge's state and possibly a freshly earned badge
 * have all moved together, and a client that refetches to find out renders a board that
 * disagrees with itself in between.
 */
@Getter
@Setter
public class BadgesResponseBody implements BaseModel {

    private List<BadgeDto> badges;

    /** The gamer's balance after whatever this call did. */
    private int coins;

    private int earned;

    /** How many exist in total, so the client can show "4 of 13" without counting. */
    private int total;

    /** How many slots a profile has. Sent so the rule lives on the server. */
    private int showcaseSlots;
}
