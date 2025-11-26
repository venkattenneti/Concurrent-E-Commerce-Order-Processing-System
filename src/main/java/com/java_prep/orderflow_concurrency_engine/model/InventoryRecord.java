package com.java_prep.orderflow_concurrency_engine.model;

import java.time.Instant;

/**
 * Represents inventory state for a product.
 * Supports two use cases:
 * 1. Live tracking: availableStock + reservedStock (no timestamp/productId needed)
 * 2. Snapshot: productId + availableStock + timestamp (for reporting)
 *
 * Thread-safe: Immutable after construction
 */
public class InventoryRecord {

    private final String productId;         // Optional: null for live tracking, set for snapshots
    private final int availableStock;       // Stock available for reservation
    private final int reservedStock;        // Stock reserved for orders (awaiting payment)
    private final Instant snapshotTime;     // Optional: null for live tracking, set for snapshots

    /**
     * Constructor for live inventory tracking (no productId or timestamp).
     * Used by InventoryManager for real-time state management.
     *
     * @param availableStock stock available for reservation
     * @param reservedStock stock reserved for orders
     */
    public InventoryRecord(int availableStock, int reservedStock) {
        this(null, availableStock, reservedStock, null);
    }

    /**
     * Constructor for snapshot with timestamp (backward compatibility).
     * Used for reporting and audit trails.
     *
     * @param productId the product ID
     * @param availableStock current available stock
     * @param snapshotTime when this snapshot was taken
     */
    public InventoryRecord(String productId, int availableStock, Instant snapshotTime) {
        this(productId, availableStock, 0, snapshotTime);
    }

    /**
     * Full constructor with all fields.
     *
     * @param productId the product ID (null for live tracking)
     * @param availableStock stock available for reservation
     * @param reservedStock stock reserved for orders
     * @param snapshotTime snapshot timestamp (null for live tracking)
     */
    public InventoryRecord(String productId, int availableStock, int reservedStock, Instant snapshotTime) {
        if (availableStock < 0) {
            throw new IllegalArgumentException("Available stock cannot be negative: " + availableStock);
        }
        if (reservedStock < 0) {
            throw new IllegalArgumentException("Reserved stock cannot be negative: " + reservedStock);
        }

        this.productId = productId;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
        this.snapshotTime = snapshotTime;
    }

    // Getters
    public String getProductId() {
        return productId;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    /**
     * Alias for getAvailableStock() to support record-like syntax.
     */
    public int availableStock() {
        return availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    /**
     * Alias for getReservedStock() to support record-like syntax.
     */
    public int reservedStock() {
        return reservedStock;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    /**
     * Gets total stock (available + reserved).
     */
    public int getTotalStock() {
        return availableStock + reservedStock;
    }

    /**
     * Alias for getTotalStock() to support record-like syntax.
     */
    public int totalStock() {
        return availableStock + reservedStock;
    }

    /**
     * Checks if this is a snapshot record (has productId and timestamp).
     */
    public boolean isSnapshot() {
        return productId != null && snapshotTime != null;
    }

    @Override
    public String toString() {
        if (isSnapshot()) {
            // Snapshot format (with timestamp)
            return String.format(
                    "InventoryRecord{Product: %s | Available: %d | Reserved: %d | Total: %d | Time: %s}",
                    productId, availableStock, reservedStock, getTotalStock(), snapshotTime
            );
        } else {
            // Live tracking format (no timestamp)
            return String.format(
                    "InventoryRecord{Available: %d | Reserved: %d | Total: %d}",
                    availableStock, reservedStock, getTotalStock()
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InventoryRecord that = (InventoryRecord) o;

        if (availableStock != that.availableStock) return false;
        if (reservedStock != that.reservedStock) return false;
        if (productId != null ? !productId.equals(that.productId) : that.productId != null) return false;
        return snapshotTime != null ? snapshotTime.equals(that.snapshotTime) : that.snapshotTime == null;
    }

    @Override
    public int hashCode() {
        int result = productId != null ? productId.hashCode() : 0;
        result = 31 * result + availableStock;
        result = 31 * result + reservedStock;
        result = 31 * result + (snapshotTime != null ? snapshotTime.hashCode() : 0);
        return result;
    }
}
