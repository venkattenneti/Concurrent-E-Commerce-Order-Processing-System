package com.java_prep.orderflow_concurrency_engine.inventory;

import com.java_prep.orderflow_concurrency_engine.model.InventoryMetrics;
import com.java_prep.orderflow_concurrency_engine.model.InventoryRecord;
import com.java_prep.orderflow_concurrency_engine.reservation.ReservationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class InventoryManager {
    private static final Logger logger = LoggerFactory.getLogger(InventoryManager.class);

    private final ConcurrentHashMap<String, AtomicInteger> inventory;
    private final ConcurrentHashMap<String, ReentrantLock> productLock;
    private final AtomicLong reservationAttempts;
    private final AtomicLong reservationSuccesses;
    private final AtomicLong reservationFailures;
    private final AtomicLong releaseOperations;
    private final AtomicLong lockContentions;

    public InventoryManager() {
        this.inventory= new ConcurrentHashMap<>();
        this.productLock= new ConcurrentHashMap<>();
        this.reservationAttempts=new AtomicLong(0);
        this.reservationSuccesses=new AtomicLong(0);
        this.reservationFailures=new AtomicLong(0);
        this.releaseOperations=new AtomicLong(0);
        this.lockContentions=new AtomicLong(0);
    }

    public void initializeInventory(Map<String,Integer> productCatalog){
        if(productCatalog==null || productCatalog.isEmpty()){
            throw new IllegalArgumentException("Product Catalog cannot be null");
        }
        logger.debug("InventoryManager.initializeInventory()- Start");
        logger.info("Initializing Inventory with Product Catalog Size:{}",productCatalog.size());
        for(Map.Entry<String,Integer> entry: productCatalog.entrySet()){
            String productID=entry.getKey();
            Integer initialStock= entry.getValue();
            if(productID==null||productID.trim().isEmpty())
                throw new IllegalArgumentException("Product ID cannot be null or empty!!");
            if (initialStock<0)
                throw new IllegalArgumentException("Initial Stock cannot be Negative for ProductID:"+productID);
            inventory.put(productID,new AtomicInteger(initialStock));
            productLock.put(productID,new ReentrantLock(true));
            logger.debug("Initialized product{} with {} units",productID,initialStock);
        }
        logger.info("Inventory initialization complete | Total products: {} | Total locks: {}", inventory.size(), productLock.size());
        logger.debug("InventoryManager.initializeInventory()- End");
    }

    public ReservationResult reserveStock(String productId, int quantity) throws Exception {
        logger.debug("InventoryManager.reserveStock()- Start");
        reservationAttempts.incrementAndGet();
        if (productId == null || productId.trim().isEmpty()) {
            logger.warn("Reserve stock failed: productId is null or empty");
            reservationFailures.incrementAndGet();
            return ReservationResult.failure(productId, quantity, 0, "Product ID cannot be null or empty");
        }
        if (quantity <= 0) {
            logger.warn("Reserve stock failed for {}: invalid quantity {}", productId, quantity);
            reservationFailures.incrementAndGet();
            return ReservationResult.failure(productId, quantity, 0, "Quantity must be greater than zero");
        }
        if (!inventory.containsKey(productId)) {
            logger.warn("Reserve stock failed: product {} not found in inventory", productId);
            reservationFailures.incrementAndGet();
            return ReservationResult.failure(productId, quantity, 0, "Product not found in inventory");
        }
        ReentrantLock lock= productLock.get(productId);
        logger.debug("Attempting to Acquire local for Product{}",productId);
        lock.lock();
        try{
            logger.debug("Lock Aquired for Product {}",productId);
            AtomicInteger stockHolder= inventory.get(productId);
            int currentStock= stockHolder.get();
            if(currentStock>=quantity){
                reservationSuccesses.incrementAndGet();
                int remainingStock= stockHolder.addAndGet(-quantity);
                logger.info("✓ Reserved {} units of {} | Remaining: {} | Thread: {}", quantity, productId, remainingStock, Thread.currentThread().getName());
                return ReservationResult.success(productId,quantity,remainingStock);
            }else{
                reservationFailures.incrementAndGet();
                logger.warn("✗ Insufficient stock for {} | Requested: {} | Available: {} | Thread: {}", productId, quantity, currentStock, Thread.currentThread().getName());
                return ReservationResult.failure(productId, quantity, currentStock, String.format("Insufficient stock. Available: %d, Requested: %d", currentStock, quantity));
            }
        }
        finally {
            lock.unlock();
            logger.debug("Lock Released for Product: {}",productId);
        }
    }

    public void releaseStock(String productId, int quantity){
        logger.debug("InventoryManager.releaseStock()- Start");
        if (productId == null || productId.trim().isEmpty()) {
            logger.error("Release stock failed: productId is null or empty");
            return;
        }
        if (quantity <= 0) {
            logger.error("Release stock failed for {}: invalid quantity {}", productId, quantity);
            return;
        }
        if (!inventory.containsKey(productId)) {
            logger.error("Release stock failed: product {} not found in inventory", productId);
            return;
        }
        ReentrantLock lock= productLock.get(productId);
        lock.lock();
        try{
            AtomicInteger stockHolder = inventory.get(productId);
            int newStock= stockHolder.addAndGet(quantity);
            releaseOperations.incrementAndGet();
            logger.info("Released {} units of {} | New Stock :{} | Thread:{}",quantity,productId,newStock,Thread.currentThread().getName());
        }finally {
            lock.unlock();
        }
    }

    public int getAvailableStock(String productId) {
        if (productId == null || !inventory.containsKey(productId)) {
            logger.warn("Get stock failed: product {} not found", productId);
            return -1;
        }
        return inventory.get(productId).get();
    }

    public boolean isProductExists(String productId) {
        return productId != null && inventory.containsKey(productId);
    }

    public List<InventoryRecord> getAllInventorySnapshot() {
        List<InventoryRecord> snapshot = new ArrayList<>();
        Instant snapshotTime = Instant.now();
        for (Map.Entry<String, AtomicInteger> entry : inventory.entrySet()) {
            String productId = entry.getKey();
            int currentStock = entry.getValue().get();
            snapshot.add(new InventoryRecord(productId, currentStock, snapshotTime));
        }
        logger.debug("Generated inventory snapshot with {} products", snapshot.size());
        return snapshot;
    }

    public int getProductCount() {
        return inventory.size();
    }

    public InventoryMetrics getMetrics() {
        long attempts = reservationAttempts.get();
        long successes = reservationSuccesses.get();
        long failures = reservationFailures.get();
        long releases = releaseOperations.get();
        long contentions = lockContentions.get();
        double successRate = attempts > 0 ? (successes * 100.0 / attempts) : 0.0;
        return new InventoryMetrics(attempts, successes, failures, releases, contentions, successRate);
    }

    public void resetMetrics() {
        reservationAttempts.set(0);
        reservationSuccesses.set(0);
        reservationFailures.set(0);
        releaseOperations.set(0);
        lockContentions.set(0);
        logger.info("Metrics reset");
    }

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

    public void shutdown() {
        logger.info("InventoryManager shutting down...");
        printMetrics();
        logger.info("Final inventory state: {} products in catalog", inventory.size());
        logger.info("InventoryManager shutdown complete");
    }
}
