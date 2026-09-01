package com.gamebuddy.billing.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the promotion codes screen shows: what is waiting, and what has been used.
 *
 * <p>Public codes never appear in either list. There is no way to ask "which codes exist
 * that I could type" — that would turn a coupon into a menu — so the screen is the gifts
 * addressed to this account, plus a field for anything the person was given elsewhere.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MyPromoCodesResponseBody implements BaseModel {

    private List<WaitingCode> waiting;
    private List<RedeemedCode> redeemed;

    /**
     * A gift this account has not taken yet.
     *
     * @param code shown in full: it was addressed to this person, and seeing it is what
     *     lets them recognise the one from the email
     */
    public record WaitingCode(
            String id, String code, String kind, Integer coinAmount, Integer goldDays, Instant expiresAt) {}

    /** Something already redeemed, so the screen is not empty after the one gift is taken. */
    public record RedeemedCode(String code, String kind, Integer coinAmount, Integer goldDays, Instant redeemedAt) {}
}
