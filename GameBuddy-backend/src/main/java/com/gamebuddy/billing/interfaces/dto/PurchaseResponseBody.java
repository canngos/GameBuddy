package com.gamebuddy.billing.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The outcome of a redemption. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseResponseBody implements BaseModel {

    private String productId;
    private String status;

    /** When the granted subscription lapses, or null for a consumable. */
    private Instant expiresAt;
}
