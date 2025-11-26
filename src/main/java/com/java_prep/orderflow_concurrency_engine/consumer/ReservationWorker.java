package com.java_prep.orderflow_concurrency_engine.consumer;

import com.java_prep.orderflow_concurrency_engine.inventory.InventoryManager;
import com.java_prep.orderflow_concurrency_engine.model.Order;
import com.java_prep.orderflow_concurrency_engine.model.OrderItem;
import com.java_prep.orderflow_concurrency_engine.model.OrderStatus;
import com.java_prep.orderflow_concurrency_engine.reservation.ReservationManager;
import com.java_prep.orderflow_concurrency_engine.reservation.ReservationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker thread that reserves inventory for validated orders and pushes successful reservations to payment queue.
 * Part of the 3-stage multi-threaded order processing pipeline.
 *
 * Pipeline Position: Stage 2
 * Input: reservationQueue (from ValidationWorker)
 * Output: paymentQueue (to PaymentWorker)
 */
public class ReservationWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ReservationWorker.class);

    private static final long POLL_TIMEOUT_MS = 1000;
    private static final long PUSH_TIMEOUT_SECONDS = 5;

    // Input queue
    private final BlockingQueue<Order> reservationQueue;

    // Output queue
    private final BlockingQueue<Order> paymentQueue;

    // Managers
    private final InventoryManager inventoryManager;
    private final ReservationManager reservationManager;

    private final CountDownLatch shutdownLatch;
    private final String workerId;

    // Metrics
    private final AtomicLong processedCount;
    private final AtomicLong reservationSuccessCount;
    private final AtomicLong reservationFailureCount;
    private final AtomicLong pushedToPaymentCount;
    private final AtomicLong pushFailedCount;

    private final AtomicBoolean isRunning;

    /**
     * Full constructor with payment queue support.
     *
     * @param reservationQueue input queue for validated orders
     * @param paymentQueue output queue for successfully reserved orders
     * @param inventoryManager manages inventory locking
     * @param reservationManager tracks reservation metadata
     * @param shutdownLatch latch for coordinated shutdown
     * @param workerId unique identifier for this worker
     */
    public ReservationWorker(BlockingQueue<Order> reservationQueue,
                             BlockingQueue<Order> paymentQueue,
                             InventoryManager inventoryManager,
                             ReservationManager reservationManager,
                             CountDownLatch shutdownLatch,
                             String workerId) {
        this.reservationQueue = reservationQueue;
        this.paymentQueue = paymentQueue;
        this.inventoryManager = inventoryManager;
        this.reservationManager = reservationManager;
        this.shutdownLatch = shutdownLatch;
        this.workerId = workerId;

        this.processedCount = new AtomicLong(0);
        this.reservationSuccessCount = new AtomicLong(0);
        this.reservationFailureCount = new AtomicLong(0);
        this.pushedToPaymentCount = new AtomicLong(0);
        this.pushFailedCount = new AtomicLong(0);
        this.isRunning = new AtomicBoolean(true);
    }

    /**
     * Constructor without payment queue (for testing).
     */
    public ReservationWorker(BlockingQueue<Order> reservationQueue,
                             InventoryManager inventoryManager,
                             ReservationManager reservationManager,
                             CountDownLatch shutdownLatch,
                             String workerId) {
        this(reservationQueue, null, inventoryManager, reservationManager, shutdownLatch, workerId);
    }

    @Override
    public void run() {
        logger.info("{} started", workerId);

        try {
            while (isRunning.get() || !reservationQueue.isEmpty()) {
                try {
                    Order order = reservationQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                    if (order != null) {
                        processOrder(order);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("{} interrupted during polling", workerId);
                    break;
                }
            }

            drainRemainingOrders();

        } catch (Exception e) {
            logger.error("{} encountered fatal error", workerId, e);
        } finally {
            shutdownLatch.countDown();
            logFinalStatistics();
        }

        logger.info("{} stopped", workerId);
    }

    /**
     * Processes a single order: reserves inventory and pushes to payment queue if successful.
     */
    private void processOrder(Order order) {
        logger.debug("{} processing order {}", workerId, order.getOrderId());

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Attempt inventory reservation
            ReservationResult result = reserveInventory(order);

            if (result.isSuccess()) {
                // Step 2: Mark as reserved
                order.setOrderStatus(OrderStatus.INVENTORY_RESERVED);
                long count = reservationSuccessCount.incrementAndGet();

                logger.debug("{} reserved inventory for order {} [Total: {}]",
                        workerId, order.getOrderId(), count);

                // Step 3: Track reservation in ReservationManager
                reservationManager.recordReservation(order.getOrderId(), result);

                // Step 4: Push to payment queue
                if (paymentQueue != null) {
                    pushToPaymentQueue(order);
                } else {
                    logger.debug("{} no payment queue configured, order {} remains in memory",
                            workerId, order.getOrderId());
                }

            } else {
                // Reservation failed - insufficient inventory
                order.setOrderStatus(OrderStatus.INVENTORY_UNAVAILABLE);
                long count = reservationFailureCount.incrementAndGet();

                logger.warn("{} reservation FAILED for order {}: {} [Total: {}]",
                        workerId, order.getOrderId(), result.getFailureReason(), count);

                // Track failure
                reservationManager.recordFailure(order.getOrderId(), result.getFailureReason());
            }

            // Performance monitoring
            long processingTime = System.currentTimeMillis() - startTime;
            if (processingTime > 100) {
                logger.warn("Slow reservation detected: {}ms for order {}",
                        processingTime, order.getOrderId());
            }

        } catch (Exception e) {
            logger.error("{} error processing order {}", workerId, order.getOrderId(), e);
            order.setOrderStatus(OrderStatus.INVENTORY_UNAVAILABLE);
            reservationFailureCount.incrementAndGet();
        } finally {
            processedCount.incrementAndGet();
        }
    }

    /**
     * Attempts to reserve inventory for all items in the order.
     */
    private ReservationResult reserveInventory(Order order) {
        logger.debug("{} attempting to reserve {} items for order {}",
                workerId, order.getItems().size(), order.getOrderId());

        // Attempt reservation through InventoryManager
        ReservationResult result = inventoryManager.reserveInventory(
                order.getOrderId(),
                order.getItems()
        );

        if (result.isSuccess()) {
            logger.debug("{} successfully reserved inventory for order {}: {}",
                    workerId, order.getOrderId(), result.getReservedItems());
        } else {
            logger.debug("{} failed to reserve inventory for order {}: {}",
                    workerId, order.getOrderId(), result.getFailureReason());
        }

        return result;
    }

    /**
     * Pushes successfully reserved order to payment queue with timeout.
     */
    private void pushToPaymentQueue(Order order) {
        try {
            boolean added = paymentQueue.offer(
                    order,
                    PUSH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            if (added) {
                long count = pushedToPaymentCount.incrementAndGet();
                logger.debug("{} pushed order {} to payment queue [Total: {}]",
                        workerId, order.getOrderId(), count);
            } else {
                // Timeout - queue might be full
                long failCount = pushFailedCount.incrementAndGet();
                logger.error("{} FAILED to push order {} to payment queue (timeout) [Total failures: {}]",
                        workerId, order.getOrderId(), failCount);

                // Rollback reservation
                rollbackReservation(order);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("{} interrupted while pushing order {} to payment queue",
                    workerId, order.getOrderId());
            pushFailedCount.incrementAndGet();

            // Rollback reservation
            rollbackReservation(order);
        }
    }

    /**
     * Rolls back inventory reservation if order cannot proceed to payment.
     */
    private void rollbackReservation(Order order) {
        logger.warn("{} rolling back reservation for order {}", workerId, order.getOrderId());

        try {
            inventoryManager.releaseReservation(order.getOrderId());
            reservationManager.recordRollback(order.getOrderId(), "Payment queue push failed");
            order.setOrderStatus(OrderStatus.RESERVATION_ROLLED_BACK);

            logger.info("{} successfully rolled back reservation for order {}",
                    workerId, order.getOrderId());

        } catch (Exception e) {
            logger.error("{} CRITICAL: Failed to rollback reservation for order {}",
                    workerId, order.getOrderId(), e);
        }
    }

    /**
     * Attempts to drain remaining orders from queue during shutdown.
     */
    private void drainRemainingOrders() {
        logger.info("{} draining remaining orders", workerId);

        int drained = 0;
        Order order;

        while ((order = reservationQueue.poll()) != null) {
            processOrder(order);
            drained++;
        }

        if (drained > 0) {
            logger.info("{} drained {} remaining orders", workerId, drained);
        }
    }

    /**
     * Logs final statistics for this worker.
     */
    private void logFinalStatistics() {
        logger.info("=== {} Final Statistics ===", workerId);
        logger.info("  Processed: {}", processedCount.get());
        logger.info("  Reservation Success: {}", reservationSuccessCount.get());
        logger.info("  Reservation Failure: {}", reservationFailureCount.get());
        logger.info("  Pushed to Payment: {}", pushedToPaymentCount.get());
        logger.info("  Push Failures: {}", pushFailedCount.get());

        // Consistency check
        if (paymentQueue != null) {
            long expected = reservationSuccessCount.get();
            long actual = pushedToPaymentCount.get();

            if (expected != actual) {
                logger.error("INCONSISTENCY: Reserved {} orders but only pushed {} to payment",
                        expected, actual);
            } else {
                logger.info("  Consistency check: PASSED (reserved = pushed)");
            }
        }

        // Success rate
        long total = reservationSuccessCount.get() + reservationFailureCount.get();
        if (total > 0) {
            double successRate = (reservationSuccessCount.get() * 100.0) / total;
            logger.info("  Success Rate: {:.1f}%", successRate);
        }
    }

    /**
     * Signals this worker to stop processing (graceful shutdown).
     */
    public void stop() {
        logger.info("{} stop signal received", workerId);
        isRunning.set(false);
    }

    // Getters for metrics
    public long getProcessedCount() { return processedCount.get(); }
    public long getReservationSuccessCount() { return reservationSuccessCount.get(); }
    public long getReservationFailureCount() { return reservationFailureCount.get(); }
    public long getPushedToPaymentCount() { return pushedToPaymentCount.get(); }
    public long getPushFailedCount() { return pushFailedCount.get(); }
}
