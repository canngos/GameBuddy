package com.gamebuddy.billing.domain;

/** A verified transfer that cannot yet be applied without risking an incorrect entitlement. */
public class TransferPendingException extends RuntimeException {

    public TransferPendingException(String message) {
        super(message);
    }
}
