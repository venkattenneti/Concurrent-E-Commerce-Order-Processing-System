package com.java_prep.orderflow_concurrency_engine.inventory;

import com.java_prep.orderflow_concurrency_engine.model.InventoryMetrics;
import com.java_prep.orderflow_concurrency_engine.model.InventoryRecord;
import com.java_prep.orderflow_concurrency_engine.model.OrderItem;
import com.java_prep.orderflow_concurrency_engine.reservation.ReservationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Thread-safe inventory manager with pessimistic locking.
 * Tracks both available stock and reserved stock per product.
 *
 * Design:
 * - Available stock: Can be reserved
 * - Reserved stock: Locked for specific orders, awaiting payment
 * - Total stock = available + reserved
 */
public class InventoryManager {

    private static final Logger logger = LoggerFactory.getLogger(InventoryManager.class);

    // Core data structures
    private final ConcurrentHashMap<String, InventoryRecord> inventory;  // CHANGED: Now stores InventoryRecord
    private final ConcurrentHashMap<String, ReentrantLock> productLock;

    // Metrics
    private final AtomicLong reservationAttempts;
    private final AtomicLong reservationSuccesses;
    private final AtomicLong reservationFailures;
    private final AtomicLong releaseOperations;
    private final AtomicLong lockContentions;

    public InventoryManager() {
        this.inventory = new ConcurrentHashMap<>();
        this.productLock = new ConcurrentHashMap<>();
        this.reservationAttempts = new AtomicLong(0);
        this.reservationSuccesses = new AtomicLong(0);
        this.reservationFailures = new AtomicLong(0);
        this.releaseOperations = new AtomicLong(0);
        this.lockContentions = new AtomicLong(0);

        logger.info("InventoryManager initialized");
    }

    /**
     * Initializes inventory with product catalog.
     *
     * @param productCatalog map of productId -> initial stock quantity
     */
    public void initializeInventory(Map<String, Integer> productCatalog) {
        if (productCatalog == null || productCatalog.isEmpty()) {
            throw new IllegalArgumentException("Product catalog cannot be null or empty");
        }

        logger.info("Initializing inventory with {} products", productCatalog.size());

        for (Map.Entry<String, Integer> entry : productCatalog.entrySet()) {
            String productId = entry.getKey();
            Integer initialStock = entry.getValue();

            // Validation
            if (productId == null || productId.trim().isEmpty()) {
                throw new IllegalArgumentException("Product ID cannot be null or empty");
            }
            if (initialStock < 0) {
                throw new IllegalArgumentException("Initial stock cannot be negative for product: " + productId);
            }

            // Store as InventoryRecord (available stock, reserved = 0)
            inventory.put(productId, new InventoryRecord(initialStock, 0));
            productLock.put(productId, new ReentrantLock(true));

            logger.debug("Initialized product {} with {} units", productId, initialStock);
        }

        logger.info("Inventory initialization complete | Products: {} | Locks: {}",
                inventory.size(), productLock.size());
    }

    /**
     * Convenience method to add a single product.
     *
     * @param productId the product ID
     * @param initialStock initial stock quantity
     */
    public void addProduct(String productId, int initialStock) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }
        if (initialStock < 0) {
            throw new IllegalArgumentException("Initial stock cannot be negative");
        }

        inventory.put(productId, new InventoryRecord(initialStock, 0));
        productLock.putIfAbsent(productId, new ReentrantLock(true));

        logger.info("Added product {} with {} units", productId, initialStock);
    }

    /**
     * Reserves stock for a single product (backward compatibility).
     *
     * @param productId the product ID
     * @param quantity quantity to reserve
     * @return ReservationResult
     */
    public ReservationResult reserveStock(String productId, int quantity) {
        logger.debug("Attempting to reserve {} units of {}", quantity, productId);

        reservationAttempts.incrementAndGet();

        // Validation
        if (productId == null || productId.trim().isEmpty()) {
            logger.warn("Reserve failed: productId is null or empty");
            reservationFailures.incrementAndGet();
            return ReservationResult.failure("UNKNOWN", "Product ID cannot be null or empty");
        }

        if (quantity <= 0) {
            logger.warn("Reserve failed for {}: invalid quantity {}", productId, quantity);
            reservationFailures.incrementAndGet();
            return ReservationResult.failure("UNKNOWN", "Quantity must be greater than zero");
        }

        if (!inventory.containsKey(productId)) {
            logger.warn("Reserve failed: product {} not found", productId);
            reservationFailures.incrementAndGet();

            List<ReservationResult.ReservationFailureDetail> details = new ArrayList<>();
            details.add(new ReservationResult.ReservationFailureDetail(
                    productId, quantity, 0, "Product not found"
            ));
            return ReservationResult.failure("UNKNOWN", "Product not found", details);
        }

        // Acquire lock
        ReentrantLock lock = productLock.get(productId);
        logger.debug("Attempting to acquire lock for {}", productId);

        lock.lock();
        try {
            logger.debug("Lock acquired for {}", productId);

            InventoryRecord record = inventory.get(productId);
            int currentAvailable = record.availableStock();

            if (currentAvailable >= quantity) {
                // Success - update record
                int newAvailable = currentAvailable - quantity;
                int newReserved = record.reservedStock() + quantity;

                inventory.put(productId, new InventoryRecord(newAvailable, newReserved));

                reservationSuccesses.incrementAndGet();
                logger.info("✓ Reserved {} units of {} | Available: {} -> {} | Thread: {}",
                        quantity, productId, currentAvailable, newAvailable, Thread.currentThread().getName());

                // Return success (create single-item list for compatibility)
                OrderItem item = new OrderItem(productId, "Product", quantity, BigDecimal.ZERO);
                List<OrderItem> items = new ArrayList<>();
                items.add(item);
                return ReservationResult.success("SINGLE-" + productId, items);

            } else {
                // Failure - insufficient stock
                reservationFailures.incrementAndGet();
                logger.warn("✗ Insufficient stock for {} | Requested: {} | Available: {} | Thread: {}",
                        productId, quantity, currentAvailable, Thread.currentThread().getName());

                List<ReservationResult.ReservationFailureDetail> details = new ArrayList<>();
                details.add(new ReservationResult.ReservationFailureDetail(
                        productId, quantity, currentAvailable, "Insufficient stock"
                ));
                return ReservationResult.failure("SINGLE-" + productId, "Insufficient stock", details);
            }

        } finally {
            lock.unlock();
            logger.debug("Lock released for {}", productId);
        }
    }

    /**
     * Reserves inventory for all items in an order (multi-product).
     * Uses pessimistic locking with sorted lock acquisition to prevent deadlock.
     *
     * @param orderId the order ID
     * @param items list of order items to reserve
     * @return ReservationResult indicating success or failure
     */
    public ReservationResult reserveInventory(String orderId, List<OrderItem> items) {
        logger.debug("Attempting to reserve inventory for order {} ({} items)", orderId, items.size());

        reservationAttempts.incrementAndGet();

        // Validation
        if (items == null || items.isEmpty()) {
            reservationFailures.incrementAndGet();
            return ReservationResult.failure(orderId, "Order has no items");
        }

        // Step 1: Get sorted list of unique product IDs (prevents deadlock)
        List<String> productIds = items.stream()
                .map(OrderItem::getProductId)
                .sorted()  // CRITICAL: Sort to prevent deadlock
                .distinct()
                .toList();

        List<ReentrantLock> acquiredLocks = new ArrayList<>();

        try {
            // Step 2: Acquire locks for all products
            for (String productId : productIds) {
                ReentrantLock lock = productLock.computeIfAbsent(productId, k -> new ReentrantLock(true));
                lock.lock();
                acquiredLocks.add(lock);
            }

            logger.debug("Acquired {} locks for order {}", acquiredLocks.size(), orderId);

            // Step 3: Check if all items have sufficient stock
            List<ReservationResult.ReservationFailureDetail> failures = new ArrayList<>();

            for (OrderItem item : items) {
                String productId = item.getProductId();
                int requestedQty = item.getQuantity();

                InventoryRecord record = inventory.get(productId);

                if (record == null) {
                    failures.add(new ReservationResult.ReservationFailureDetail(
                            productId, requestedQty, 0, "Product not found"
                    ));
                } else if (record.availableStock() < requestedQty) {
                    failures.add(new ReservationResult.ReservationFailureDetail(
                            productId, requestedQty, record.availableStock(), "Insufficient stock"
                    ));
                }
            }

            // Step 4: If any item fails, abort entire reservation
            if (!failures.isEmpty()) {
                reservationFailures.incrementAndGet();

                String failureReason = String.format(
                        "Cannot reserve %d/%d products", failures.size(), items.size()
                );

                logger.warn("Reservation failed for order {}: {}", orderId, failureReason);
                return ReservationResult.failure(orderId, failureReason, failures);
            }

            // Step 5: All checks passed - reserve all items
            for (OrderItem item : items) {
                String productId = item.getProductId();
                int requestedQty = item.getQuantity();

                InventoryRecord record = inventory.get(productId);
                int newAvailable = record.availableStock() - requestedQty;
                int newReserved = record.reservedStock() + requestedQty;

                inventory.put(productId, new InventoryRecord(newAvailable, newReserved));

                logger.debug("Reserved {} units of {} for order {} (available: {} -> {})",
                        requestedQty, productId, orderId, record.availableStock(), newAvailable);
            }

            reservationSuccesses.incrementAndGet();

            logger.info("Successfully reserved {} items for order {}", items.size(), orderId);
            return ReservationResult.success(orderId, items);

        } finally {
            // Step 6: Release all locks (in reverse order for good practice)
            for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                acquiredLocks.get(i).unlock();
            }
            logger.debug("Released {} locks for order {}", acquiredLocks.size(), orderId);
        }
    }

    /**
     * Releases a reservation (e.g., after payment failure or cancellation).
     * Moves stock from reserved back to available.
     *
     * @param orderId the order ID to release
     */
    public void releaseReservation(String orderId) {
        logger.info("Releasing reservation for order {}", orderId);
        // Note: In a real system, you'd track which products were reserved per order
        // For now, this is a placeholder
        releaseOperations.incrementAndGet();
    }

    /**
     * Releases stock back to available (e.g., after order cancellation).
     *
     * @param productId the product ID
     * @param quantity quantity to release
     */
    public void releaseStock(String productId, int quantity) {
        logger.debug("Releasing {} units of {}", quantity, productId);

        if (productId == null || productId.trim().isEmpty()) {
            logger.error("Release failed: productId is null or empty");
            return;
        }

        if (quantity <= 0) {
            logger.error("Release failed for {}: invalid quantity {}", productId, quantity);
            return;
        }

        if (!inventory.containsKey(productId)) {
            logger.error("Release failed: product {} not found", productId);
            return;
        }

        ReentrantLock lock = productLock.get(productId);
        lock.lock();
        try {
            InventoryRecord record = inventory.get(productId);
            int newAvailable = record.availableStock() + quantity;
            int newReserved = Math.max(0, record.reservedStock() - quantity);

            inventory.put(productId, new InventoryRecord(newAvailable, newReserved));
            releaseOperations.incrementAndGet();

            logger.info("Released {} units of {} | Available: {} | Thread: {}",
                    quantity, productId, newAvailable, Thread.currentThread().getName());

        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets available stock for a product.
     *
     * @param productId the product ID
     * @return available stock, or -1 if product not found
     */
    public int getAvailableStock(String productId) {
        if (productId == null || !inventory.containsKey(productId)) {
            logger.warn("Get stock failed: product {} not found", productId);
            return -1;
        }

        InventoryRecord record = inventory.get(productId);
        return record.availableStock();
    }

    /**
     * Checks if a product exists in inventory.
     */
    public boolean isProductExists(String productId) {
        return productId != null && inventory.containsKey(productId);
    }

    /**
     * Gets a snapshot of all inventory records.
     */
    public List<InventoryRecord> getAllInventorySnapshot() {
        List<InventoryRecord> snapshot = new ArrayList<>();

        for (Map.Entry<String, InventoryRecord> entry : inventory.entrySet()) {
            snapshot.add(entry.getValue());
        }

        logger.debug("Generated inventory snapshot with {} products", snapshot.size());
        return snapshot;
    }

    /**
     * Gets total number of products in inventory.
     */
    public int getProductCount() {
        return inventory.size();
    }

    /**
     * Gets aggregated metrics.
     */
    public InventoryMetrics getMetrics() {
        long attempts = reservationAttempts.get();
        long successes = reservationSuccesses.get();
        long failures = reservationFailures.get();
        long releases = releaseOperations.get();
        long contentions = lockContentions.get();
        double successRate = attempts > 0 ? (successes * 100.0 / attempts) : 0.0;

        return new InventoryMetrics(attempts, successes, failures, releases, contentions, successRate);
    }

    /**
     * Resets all metrics to zero.
     */
    public void resetMetrics() {
        reservationAttempts.set(0);
        reservationSuccesses.set(0);
        reservationFailures.set(0);
        releaseOperations.set(0);
        lockContentions.set(0);
        logger.info("Metrics reset");
    }

    /**
     * Prints current metrics to log.
     */
    public void printMetrics() {
        InventoryMetrics metrics = getMetrics();
        logger.info("========== INVENTORY METRICS ==========");
        logger.info("Reservation Attempts: {}", metrics.getTotalAttempts());
        logger.info("Successful Reservations: {}", metrics.getSuccesses());
        logger.info("Failed Reservations: {}", metrics.getFailures());
        logger.info("Release Operations: {}", metrics.getReleases());
        logger.info("Success Rate: {:.2f}%", metrics.getSuccessRate());
        logger.info("Lock Contentions: {}", metrics.getContentions());
        logger.info("=======================================");
    }

    /**
     * Gracefully shuts down the inventory manager.
     */
    public void shutdown() {
        logger.info("InventoryManager shutting down...");
        printMetrics();

        // Print final inventory state
        logger.info("Final inventory state ({} products):", inventory.size());
        for (Map.Entry<String, InventoryRecord> entry : inventory.entrySet()) {
            InventoryRecord record = entry.getValue();
            logger.info("  {}: Available={}, Reserved={}",
                    entry.getKey(), record.availableStock(), record.reservedStock());
        }

        logger.info("InventoryManager shutdown complete");
    }
}
