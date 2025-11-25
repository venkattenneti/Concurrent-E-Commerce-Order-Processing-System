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
    private static final int SHUTDOWN_GRACE_PERIOD_MS=5000;

    private final BlockingQueue<Order> orderBlockingQueue;
    private final OrderValidator orderValidator;

    private final AtomicLong validatedCount;
    private final AtomicLong failedCount;
    private final AtomicLong processedCount;

    private final AtomicBoolean isRunning;

    public ValidationWorker(BlockingQueue<Order> orderBlockingQueue, CountDownLatch shutDownLatch, String workerId) {
        this.orderBlockingQueue = orderBlockingQueue;
        this.orderValidator=new OrderValidator();
        this.shutDownLatch = shutDownLatch;
        this.workerId = workerId;

        this.validatedCount=new AtomicLong(0);
        this.failedCount=new AtomicLong(0);
        this.processedCount=new AtomicLong(0);

        this.isRunning=new AtomicBoolean(true);
    }

    public ValidationWorker(BlockingQueue<Order> orderBlockingQueue, CountDownLatch shutDownLatch){
        this(orderBlockingQueue,shutDownLatch,"ValidationWorker-1");
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

    private void drainRemainingOrders() {
    }

    private void logFinalStatistics() {
    }
}
