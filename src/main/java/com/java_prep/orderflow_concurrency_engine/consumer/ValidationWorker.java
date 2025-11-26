package com.java_prep.orderflow_concurrency_engine.consumer;

import com.java_prep.orderflow_concurrency_engine.model.Order;
import com.java_prep.orderflow_concurrency_engine.model.OrderStatus;
import com.java_prep.orderflow_concurrency_engine.validation.OrderValidator;
import com.java_prep.orderflow_concurrency_engine.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ValidationWorker implements Runnable{
    private static final Logger logger= LoggerFactory.getLogger(ValidationWorker.class);

    private static final long POLL_TIMEOUT_MS=1000;
    private static final long PUSH_TIMEOUT_SECONDS = 5;
    private static final int SHUTDOWN_GRACE_PERIOD_MS=5000;

    private final BlockingQueue<Order> orderBlockingQueue;
    private final BlockingQueue<Order> reservationQueue;
    private final OrderValidator orderValidator;

    private final AtomicLong validatedCount;
    private final AtomicLong failedCount;
    private final AtomicLong processedCount;
    private final AtomicLong pushedToReservationCount;
    private final AtomicLong pushFailedCount;

    private final AtomicBoolean isRunning;

    public ValidationWorker(BlockingQueue<Order> orderBlockingQueue,BlockingQueue<Order> reservationQueue, CountDownLatch shutDownLatch, String workerId) {
        this.orderBlockingQueue = orderBlockingQueue;
        this.orderValidator=new OrderValidator();
        this.shutDownLatch = shutDownLatch;
        this.workerId = workerId;
        this.reservationQueue = reservationQueue;
        this.validatedCount=new AtomicLong(0);
        this.failedCount=new AtomicLong(0);
        this.processedCount=new AtomicLong(0);
        this.pushedToReservationCount = new AtomicLong(0);
        this.pushFailedCount = new AtomicLong(0);
        this.isRunning=new AtomicBoolean(true);
    }

    public ValidationWorker(BlockingQueue<Order> orderBlockingQueue,BlockingQueue<Order> reservationQueue, CountDownLatch shutDownLatch){
        this(orderBlockingQueue,reservationQueue,shutDownLatch,"ValidationWorker-1");
    }

    private final CountDownLatch shutDownLatch;

    private final String workerId;

    @Override
    public void run() {
        logger.debug("ValidationWorker.run()- Start. WorkerId:{}",workerId);
        try{
            while (isRunning.get()||!orderBlockingQueue.isEmpty()){
                try{
                    Order order=orderBlockingQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if(order!=null){
                        processOrder(order);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("{} Interrupted during polling",workerId);
                }
            }
            drainRemainingOrders();
        }catch (Exception e){
            logger.error("{} encountered fatal error.",workerId,e);
        }finally {
            shutDownLatch.countDown();
            logFinalStatistics();
        }
        logger.debug("ValidationWorker.run()- End. WorkerId:{}",workerId);
    }

    private void processOrder(Order order) {
        logger.debug("ValidationWorker.processOrder()- Start. WorkerId:{}",workerId);
        long startTime = System.currentTimeMillis();
        try{
            ValidationResult result=orderValidator.validate(order);
            if(result.isValid()){
                order.setOrderStatus(OrderStatus.VALIDATED);
                if (reservationQueue != null) {
                    pushToReservationQueue(order);
                } else {
                    logger.debug("{} no reservation queue configured, order {} remains in memory", workerId, order.getOrderId());
                }
                long count= validatedCount.incrementAndGet();
                logger.debug("{} validated order {} successfully [Total: {}]", workerId, order.getOrderId(), count);
            }else{
                order.setOrderStatus(OrderStatus.VALIDATION_FAILED);
                long count= failedCount.incrementAndGet();
                logger.warn("{} validation FAILED for order {}: {} [Total: {}]", workerId, order.getOrderId(), result.getErrors(), count);
            }
            long processingTime=System.currentTimeMillis()-startTime;
            if(processingTime>100)
                logger.warn("Slow Validation Detected: {}ms for order {}",processingTime,order.getOrderId());

        }catch(Exception e){
            logger.error("{} error processing order {}", workerId, order.getOrderId(), e);
            order.setOrderStatus(OrderStatus.VALIDATION_FAILED);
            failedCount.incrementAndGet();
        }finally {
            processedCount.incrementAndGet();
        }
        logger.debug("ValidationWorker.processOrder()- End. WorkerId:{}",workerId);
    }

    private void pushToReservationQueue(Order order) {
        try {
            boolean added = reservationQueue.offer(order, PUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (added) {
                long count = pushedToReservationCount.incrementAndGet();
                logger.debug("{} pushed order {} to reservation queue [Total: {}]", workerId, order.getOrderId(), count);
            } else {
                long failCount = pushFailedCount.incrementAndGet();
                logger.error("{} FAILED to push order {} to reservation queue (timeout) [Total failures: {}]", workerId, order.getOrderId(), failCount);
                order.setOrderStatus(OrderStatus.VALIDATION_FAILED);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("{} interrupted while pushing order {} to reservation queue", workerId, order.getOrderId());
            pushFailedCount.incrementAndGet();
        }
    }

    private void drainRemainingOrders() {
        logger.info("{} draining remaining orders", workerId);
        int drained = 0;
        Order order;
        while ((order = orderBlockingQueue.poll()) != null) {
            processOrder(order);
            drained++;
        }
        if (drained > 0) {
            logger.info("{} drained {} remaining orders", workerId, drained);
        }
    }

    private void logFinalStatistics() {
        logger.info("=== {} Final Statistics ===", workerId);
        logger.info("  Processed: {}", processedCount.get());
        logger.info("  Validated: {}", validatedCount.get());
        logger.info("  Failed: {}", failedCount.get());
        logger.info("  Pushed to Reservation: {}", pushedToReservationCount.get());
        logger.info("  Push Failures: {}", pushFailedCount.get());
        if (reservationQueue != null) {
            long expected = validatedCount.get();
            long actual = pushedToReservationCount.get();
            if (expected != actual) {
                logger.error("INCONSISTENCY: Validated {} orders but only pushed {} to reservation", expected, actual);
            } else {
                logger.info("  Consistency check: PASSED (validated = pushed)");
            }
        }
    }

    public void stop() {
        logger.info("{} stop signal received", workerId);
        isRunning.set(false);
    }

    public long getValidatedCount() { return validatedCount.get(); }
    public long getFailedCount() { return failedCount.get(); }
    public long getProcessedCount() { return processedCount.get(); }
    public long getPushedToReservationCount() { return pushedToReservationCount.get(); }
    public long getPushFailedCount() { return pushFailedCount.get(); }
}
