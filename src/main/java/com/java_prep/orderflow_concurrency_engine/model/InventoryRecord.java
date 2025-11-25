package com.java_prep.orderflow_concurrency_engine.model;

import java.time.Instant;

public class InventoryRecord {

    private final String productId;
    private final int availableStock;
    private final Instant snapshotTime;

    public InventoryRecord(String productId, int availableStock, Instant snapshotTime) {
        this.productId = productId;
        this.availableStock = availableStock;
        this.snapshotTime = snapshotTime;
    }

    public String getProductId() {
        return productId;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    @Override
    public String toString() {
        return String.format("InventoryRecord{Product: %s | Stock: %d | Time: %s}",
                productId, availableStock, snapshotTime);
    }
}
