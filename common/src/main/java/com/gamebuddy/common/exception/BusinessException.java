package com.gamebuddy.common.exception;

import com.gamebuddy.common.enums.TransactionCode;
import lombok.Getter;

/** Carries a {@link TransactionCode} out to the {@code GlobalExceptionHandler}. */
@Getter
public class BusinessException extends RuntimeException {

    private final transient TransactionCode transactionCode;

    public BusinessException(TransactionCode transactionCode) {
        super(transactionCode.getMessage());
        this.transactionCode = transactionCode;
    }

    public BusinessException(TransactionCode transactionCode, String detail) {
        super(transactionCode.getMessage() + ": " + detail);
        this.transactionCode = transactionCode;
    }

    public BusinessException(TransactionCode transactionCode, Throwable cause) {
        super(transactionCode.getMessage(), cause);
        this.transactionCode = transactionCode;
    }

    public BusinessException(TransactionCode transactionCode, String detail, Throwable cause) {
        super(transactionCode.getMessage() + ": " + detail, cause);
        this.transactionCode = transactionCode;
    }
}
