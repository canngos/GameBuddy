package com.gamebuddy.common.base;

import com.gamebuddy.common.enums.TransactionCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Status block returned alongside every response body. */
@Getter
@Setter
@NoArgsConstructor
public class Status {

    /** Application-level code as a string, e.g. "100" for success. */
    private String code;

    private String message;
    private boolean success;

    public Status(TransactionCode transactionCode) {
        this.code = String.valueOf(transactionCode.getId());
        this.message = transactionCode.getMessage();
        this.success = transactionCode == TransactionCode.DEFAULT_100;
    }
}
