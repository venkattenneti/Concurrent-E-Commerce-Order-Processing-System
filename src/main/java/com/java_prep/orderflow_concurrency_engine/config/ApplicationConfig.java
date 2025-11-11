package com.java_prep.orderflow_concurrency_engine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized configuration for the OrderFlow Concurrency Engine.
 * All system parameters are defined here for easy tuning and testing.
 * Configuration is immutable after creation for thread-safety.
 */
public class ApplicationConfig {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

    // ========== ORDER GENERATION CONFIG ==========
    private final int orderGenerationRate;
    private final int vipPercentage;
    private final int premiumPercentage;
    private final int regularPercentage;

    // ========== QUEUE CONFIG ==========
    private final int orderQueueCapacity;

    // ========== THREAD POOL CONFIG ==========
    private final int validationPoolSize;
    private final int reservationPoolSize;
    private final int fulfillmentPoolSize;

    // ========== TIMING CONFIG ==========
    private final int reservationExpirationMinutes;
    private final int paymentTimeoutSeconds;

    /**
     * Creates configuration with default values suitable for local testing.
     */
    public ApplicationConfig() {
        this.orderGenerationRate = 100;
        this.vipPercentage = 5;
        this.premiumPercentage = 20;
        this.regularPercentage = 75;
        this.orderQueueCapacity = 10000;
        this.validationPoolSize = 8;
        this.reservationPoolSize = 12;
        this.fulfillmentPoolSize = 10;
        this.reservationExpirationMinutes = 10;
        this.paymentTimeoutSeconds = 5;

        logger.info("ApplicationConfig initialized with default values");
        logConfiguration();
    }

    /**
     * Creates configuration with custom order generation parameters.
     * Useful for testing different scenarios (flash sale vs normal traffic).
     */
    public ApplicationConfig(int orderGenerationRate,
                             int vipPercentage,
                             int premiumPercentage) {
        this.orderGenerationRate = orderGenerationRate;
        this.vipPercentage = vipPercentage;
        this.premiumPercentage = premiumPercentage;
        this.regularPercentage = 100 - vipPercentage - premiumPercentage;

        // Use defaults for other parameters
        this.orderQueueCapacity = 10000;
        this.validationPoolSize = 8;
        this.reservationPoolSize = 12;
        this.fulfillmentPoolSize = 10;
        this.reservationExpirationMinutes = 10;
        this.paymentTimeoutSeconds = 5;

        validateConfiguration();
        logger.info("ApplicationConfig initialized with custom values");
        logConfiguration();
    }

    private void validateConfiguration() {
        if (orderGenerationRate <= 0) {
            throw new IllegalArgumentException(
                    "Order generation rate must be positive, got: " + orderGenerationRate);
        }

        int totalPercentage = vipPercentage + premiumPercentage + regularPercentage;
        if (totalPercentage != 100) {
            throw new IllegalArgumentException(
                    "Customer percentages must sum to 100, got: " + totalPercentage);
        }

        if (orderQueueCapacity <= 0) {
            throw new IllegalArgumentException(
                    "Queue capacity must be positive, got: " + orderQueueCapacity);
        }
    }

    private void logConfiguration() {
        logger.info("========== OrderFlow Configuration ==========");
        logger.info("Order Generation Rate: {} orders/sec", orderGenerationRate);
        logger.info("Customer Distribution: VIP={}%, Premium={}%, Regular={}%",
                vipPercentage, premiumPercentage, regularPercentage);
        logger.info("Order Queue Capacity: {}", orderQueueCapacity);
        logger.info("Thread Pool Sizes: Validation={}, Reservation={}, Fulfillment={}",
                validationPoolSize, reservationPoolSize, fulfillmentPoolSize);
        logger.info("Timing: Reservation={}min, Payment={}sec",
                reservationExpirationMinutes, paymentTimeoutSeconds);
        logger.info("===========================================");
    }

    // ========== GETTERS ==========

    public int getOrderGenerationRate() { return orderGenerationRate; }
    public int getVipPercentage() { return vipPercentage; }
    public int getPremiumPercentage() { return premiumPercentage; }
    public int getRegularPercentage() { return regularPercentage; }
    public int getOrderQueueCapacity() { return orderQueueCapacity; }
    public int getValidationPoolSize() { return validationPoolSize; }
    public int getReservationPoolSize() { return reservationPoolSize; }
    public int getFulfillmentPoolSize() { return fulfillmentPoolSize; }
    public int getReservationExpirationMinutes() { return reservationExpirationMinutes; }
    public int getPaymentTimeoutSeconds() { return paymentTimeoutSeconds; }
}

