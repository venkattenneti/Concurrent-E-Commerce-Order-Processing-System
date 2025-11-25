package com.java_prep.orderflow_concurrency_engine.reservation;

import java.time.Instant;

public class ReservationResult {

    private final boolean success;
    private final String productId;
    private final int requestedQuantity;
    private final int availableStock;
    private final String errorMessage;
    private final Instant timestamp;

    private ReservationResult(boolean success, String productId, int requestedQuantity,
                              int availableStock, String errorMessage) {
        this.success = success;
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableStock = availableStock;
        this.errorMessage = errorMessage;
        this.timestamp = Instant.now();
    }

    public static ReservationResult success(String productId, int requestedQuantity, int remainingStock) {
        return new ReservationResult(true, productId, requestedQuantity, remainingStock, null);
    }

    public static ReservationResult failure(String productId, int requestedQuantity,
                                            int availableStock, String errorMessage) {
        return new ReservationResult(false, productId, requestedQuantity, availableStock, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("ReservationResult{SUCCESS | Product: %s | Reserved: %d | Remaining: %d | Time: %s}",
                    productId, requestedQuantity, availableStock, timestamp);
        } else {
            return String.format("ReservationResult{FAILURE | Product: %s | Requested: %d | Available: %d | Error: %s | Time: %s}",
                    productId, requestedQuantity, availableStock, errorMessage, timestamp);
        }
    }
}
