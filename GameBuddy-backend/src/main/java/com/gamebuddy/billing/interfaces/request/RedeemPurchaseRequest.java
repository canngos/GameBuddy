package com.gamebuddy.billing.interfaces.request;

import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A client asking for a purchase to be honoured.
 *
 * <p>Note what is <em>not</em> here: no price, no expiry, no "already verified" flag, and
 * no user id. Everything that decides what the gamer receives comes from the store's
 * answer or from the authenticated principal — never from this body. A field here is a
 * field an attacker controls.
 */
@Getter
@Setter
@NoArgsConstructor
public class RedeemPurchaseRequest {

    @NotNull(message = "platform is required")
    private PurchasePlatform platform;

    /** Must match a {@code Product} store id; checked against the receipt as well. */
    @NotBlank(message = "productId is required")
    @Size(max = 128)
    private String productId;

    /** The opaque payload from the store SDK. */
    @NotBlank(message = "receipt is required")
    @Size(max = 8192)
    private String receipt;
}
