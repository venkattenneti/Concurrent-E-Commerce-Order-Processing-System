package com.java_prep.orderflow_concurrency_engine.model;

public enum OrderStatus {
    PENDING,
    VALIDATED,
    RESERVED,
    PAID,
    FULFILLED,
    SHIPPED,
    VALIDATION_FAILED,
    RESERVATION_FAILED,
    PAYMENT_FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isFailureState() {
        return this == VALIDATION_FAILED || this == RESERVATION_FAILED || this == PAYMENT_FAILED || this == CANCELLED || this == EXPIRED;
    }

    public boolean isTerminalState() {
        return this == SHIPPED || isFailureState();
    }

    public boolean isSuccessState() {
        return !isFailureState();
    }
}
