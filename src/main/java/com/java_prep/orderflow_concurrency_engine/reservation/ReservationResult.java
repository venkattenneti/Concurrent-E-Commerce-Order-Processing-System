package com.java_prep.orderflow_concurrency_engine.reservation;

import com.java_prep.orderflow_concurrency_engine.model.OrderItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of an inventory reservation attempt for an entire order.
 * Handles multi-product reservations (multiple OrderItems per order).
 *
 * Design:
 * - Success: All items in the order were successfully reserved
 * - Failure: One or more items could not be reserved (insufficient inventory)
 *
 * Thread-safe: Immutable after construction
 */
public class ReservationResult {

    private final boolean success;
    private final String orderId;
    private final List<OrderItem> reservedItems;  // Items that were successfully reserved
    private final List<ReservationFailureDetail> failureDetails;  // Details of failed items
    private final String failureReason;  // High-level failure reason
    private final Instant timestamp;

    /**
     * Private constructor - use static factory methods.
     */
    private ReservationResult(boolean success,
                              String orderId,
                              List<OrderItem> reservedItems,
                              List<ReservationFailureDetail> failureDetails,
                              String failureReason) {
        this.success = success;
        this.orderId = orderId;
        this.reservedItems = reservedItems != null ?
                Collections.unmodifiableList(new ArrayList<>(reservedItems)) :
                Collections.emptyList();
        this.failureDetails = failureDetails != null ?
                Collections.unmodifiableList(new ArrayList<>(failureDetails)) :
                Collections.emptyList();
        this.failureReason = failureReason;
        this.timestamp = Instant.now();
    }

    /**
     * Creates a successful reservation result.
     *
     * @param orderId the order ID
     * @param reservedItems list of items that were reserved
     * @return success result
     */
    public static ReservationResult success(String orderId, List<OrderItem> reservedItems) {
        return new ReservationResult(true, orderId, reservedItems, null, null);
    }

    /**
     * Creates a failed reservation result.
     *
     * @param orderId the order ID
     * @param failureReason high-level reason for failure
     * @param failureDetails detailed failure information per product
     * @return failure result
     */
    public static ReservationResult failure(String orderId,
                                            String failureReason,
                                            List<ReservationFailureDetail> failureDetails) {
        return new ReservationResult(false, orderId, null, failureDetails, failureReason);
    }

    /**
     * Creates a failed reservation result with simple reason (no detailed breakdown).
     *
     * @param orderId the order ID
     * @param failureReason reason for failure
     * @return failure result
     */
    public static ReservationResult failure(String orderId, String failureReason) {
        return new ReservationResult(false, orderId, null, null, failureReason);
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getOrderId() {
        return orderId;
    }

    public List<OrderItem> getReservedItems() {
        return reservedItems;
    }

    public List<ReservationFailureDetail> getFailureDetails() {
        return failureDetails;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Gets total quantity of items reserved (sum across all products).
     */
    public int getTotalReservedQuantity() {
        return reservedItems.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    /**
     * Gets number of unique products reserved.
     */
    public int getReservedProductCount() {
        return reservedItems.size();
    }

    @Override
    public String toString() {
        if (success) {
            return String.format(
                    "ReservationResult{SUCCESS | Order: %s | Products: %d | TotalQty: %d | Time: %s}",
                    orderId,
                    reservedItems.size(),
                    getTotalReservedQuantity(),
                    timestamp
            );
        } else {
            return String.format(
                    "ReservationResult{FAILURE | Order: %s | Reason: %s | FailedProducts: %d | Time: %s}",
                    orderId,
                    failureReason,
                    failureDetails.size(),
                    timestamp
            );
        }
    }

    /**
     * Detailed information about a failed product reservation.
     */
    public static class ReservationFailureDetail {
        private final String productId;
        private final int requestedQuantity;
        private final int availableStock;
        private final String reason;

        public ReservationFailureDetail(String productId,
                                        int requestedQuantity,
                                        int availableStock,
                                        String reason) {
            this.productId = productId;
            this.requestedQuantity = requestedQuantity;
            this.availableStock = availableStock;
            this.reason = reason;
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

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return String.format(
                    "FailureDetail{Product: %s | Requested: %d | Available: %d | Reason: %s}",
                    productId, requestedQuantity, availableStock, reason
            );
        }
    }
}
