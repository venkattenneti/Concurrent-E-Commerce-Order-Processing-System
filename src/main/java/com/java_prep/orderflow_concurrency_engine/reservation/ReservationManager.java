package com.java_prep.orderflow_concurrency_engine.reservation;

import com.java_prep.orderflow_concurrency_engine.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages reservation tracking and analytics for the order processing pipeline.
 * Provides visibility into reservation successes, failures, and rollbacks.
 *
 * Thread-safe for concurrent access by multiple ReservationWorker threads.
 */
public class ReservationManager {

    private static final Logger logger = LoggerFactory.getLogger(ReservationManager.class);

    // Reservation records indexed by order ID
    private final Map<String, ReservationRecord> reservations;

    // Metrics
    private final AtomicLong totalReservationAttempts;
    private final AtomicLong totalReservationSuccesses;
    private final AtomicLong totalReservationFailures;
    private final AtomicLong totalRollbacks;

    public ReservationManager() {
        this.reservations = new ConcurrentHashMap<>();
        this.totalReservationAttempts = new AtomicLong(0);
        this.totalReservationSuccesses = new AtomicLong(0);
        this.totalReservationFailures = new AtomicLong(0);
        this.totalRollbacks = new AtomicLong(0);

        logger.info("ReservationManager initialized");
    }

    /**
     * Records a successful reservation.
     */
    public void recordReservation(String orderId, ReservationResult result) {
        totalReservationAttempts.incrementAndGet();
        totalReservationSuccesses.incrementAndGet();

        ReservationRecord record = new ReservationRecord(
                orderId,
                result.getReservedItems(),
                ReservationStatus.SUCCESS,
                Instant.now(),
                null
        );

        reservations.put(orderId, record);

        logger.debug("Recorded successful reservation for order {}", orderId);
    }

    /**
     * Records a failed reservation attempt.
     */
    public void recordFailure(String orderId, String reason) {
        totalReservationAttempts.incrementAndGet();
        totalReservationFailures.incrementAndGet();

        ReservationRecord record = new ReservationRecord(
                orderId,
                null,
                ReservationStatus.FAILED,
                Instant.now(),
                reason
        );

        reservations.put(orderId, record);

        logger.debug("Recorded failed reservation for order {}: {}", orderId, reason);
    }

    /**
     * Records a reservation rollback.
     */
    public void recordRollback(String orderId, String reason) {
        totalRollbacks.incrementAndGet();

        ReservationRecord existing = reservations.get(orderId);

        if (existing != null) {
            ReservationRecord updated = new ReservationRecord(
                    orderId,
                    existing.getReservedItems(),
                    ReservationStatus.ROLLED_BACK,
                    existing.getTimestamp(),
                    reason
            );

            reservations.put(orderId, updated);
            logger.debug("Recorded rollback for order {}: {}", orderId, reason);
        } else {
            logger.warn("Attempted to rollback non-existent reservation: {}", orderId);
        }
    }

    /**
     * Gets reservation record for an order.
     */
    public ReservationRecord getReservation(String orderId) {
        return reservations.get(orderId);
    }

    /**
     * Checks if an order has a successful reservation.
     */
    public boolean hasReservation(String orderId) {
        ReservationRecord record = reservations.get(orderId);
        return record != null && record.getStatus() == ReservationStatus.SUCCESS;
    }

    /**
     * Gets total number of tracked reservations.
     */
    public int getReservationCount() {
        return reservations.size();
    }

    /**
     * Gets aggregated metrics.
     */
    public ReservationMetrics getMetrics() {
        return new ReservationMetrics(
                totalReservationAttempts.get(),
                totalReservationSuccesses.get(),
                totalReservationFailures.get(),
                totalRollbacks.get()
        );
    }

    /**
     * Clears all reservation records (for testing).
     */
    public void clear() {
        reservations.clear();
        totalReservationAttempts.set(0);
        totalReservationSuccesses.set(0);
        totalReservationFailures.set(0);
        totalRollbacks.set(0);
        logger.info("ReservationManager cleared");
    }

    /**
     * Prints comprehensive statistics.
     */
    public void printStatistics() {
        logger.info("\n=== RESERVATION MANAGER STATISTICS ===");
        logger.info("Total Attempts: {}", totalReservationAttempts.get());
        logger.info("Successes: {}", totalReservationSuccesses.get());
        logger.info("Failures: {}", totalReservationFailures.get());
        logger.info("Rollbacks: {}", totalRollbacks.get());
        logger.info("Tracked Reservations: {}", reservations.size());

        long total = totalReservationSuccesses.get() + totalReservationFailures.get();
        if (total > 0) {
            double successRate = (totalReservationSuccesses.get() * 100.0) / total;
            logger.info("Success Rate: {:.1f}%", successRate);
        }

        logger.info("======================================\n");
    }

    /**
     * Enum for reservation status.
     */
    public enum ReservationStatus {
        SUCCESS,
        FAILED,
        ROLLED_BACK
    }

    /**
     * Record of a reservation attempt.
     */
    public static class ReservationRecord {
        private final String orderId;
        private final List<OrderItem> reservedItems;
        private final ReservationStatus status;
        private final Instant timestamp;
        private final String failureReason;

        public ReservationRecord(String orderId, List<OrderItem> reservedItems,
                                 ReservationStatus status, Instant timestamp,
                                 String failureReason) {
            this.orderId = orderId;
            this.reservedItems = reservedItems;
            this.status = status;
            this.timestamp = timestamp;
            this.failureReason = failureReason;
        }

        public String getOrderId() { return orderId; }
        public List<OrderItem> getReservedItems() { return reservedItems; }
        public ReservationStatus getStatus() { return status; }
        public Instant getTimestamp() { return timestamp; }
        public String getFailureReason() { return failureReason; }
    }

    /**
     * Aggregated metrics snapshot.
     */
    public static class ReservationMetrics {
        private final long attempts;
        private final long successes;
        private final long failures;
        private final long rollbacks;

        public ReservationMetrics(long attempts, long successes, long failures, long rollbacks) {
            this.attempts = attempts;
            this.successes = successes;
            this.failures = failures;
            this.rollbacks = rollbacks;
        }

        public long getAttempts() { return attempts; }
        public long getSuccesses() { return successes; }
        public long getFailures() { return failures; }
        public long getRollbacks() { return rollbacks; }

        public double getSuccessRate() {
            long total = successes + failures;
            return total > 0 ? (successes * 100.0) / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "ReservationMetrics{attempts=%d, successes=%d, failures=%d, rollbacks=%d, successRate=%.1f%%}",
                    attempts, successes, failures, rollbacks, getSuccessRate()
            );
        }
    }
}
