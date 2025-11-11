package com.java_prep.orderflow_concurrency_engine.producer;

import com.java_prep.orderflow_concurrency_engine.config.ApplicationConfig;
import com.java_prep.orderflow_concurrency_engine.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class OrderProducer implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    private final BlockingQueue<Order> orderQueue;
    private final OrderGenerator orderGenerator;
    private final ApplicationConfig applicationConfig;

    private final AtomicLong orderProduced;
    private volatile boolean running;

    public OrderProducer(BlockingQueue<Order> orderQueue, OrderGenerator orderGenerator, ApplicationConfig applicationConfig) {
        this.orderQueue = orderQueue;
        this.orderGenerator = orderGenerator;
        this.applicationConfig = applicationConfig;
        this.orderProduced=new AtomicLong(0);
        this.running=true;

        logger.debug("OrderProducer Initalized with Target Rate:{}orders/sec",applicationConfig.getOrderGenerationRate());
    }

    @Override
    public void run() {
        logger.debug("OrderProducer started on Thread:{}",Thread.currentThread().getName());
        long delayMs = calculateDelayBetweenOrders();
        while(running){
            try{
                Order order= orderGenerator.generateOrder();
                boolean submitted= orderQueue.offer(order,5, TimeUnit.SECONDS);
                if(submitted){
                    long count= orderProduced.incrementAndGet();
                    logger.debug("Producer Submitted Order: {}",order.getOrderId());
                    logger.debug("Total Orders Produced:{}",count);
                }else {
                    logger.warn("Failed to submit Order: {} - due to Queue Full(TimeOut reached)",order.getOrderId());
                }
            }catch (InterruptedException e) {
                logger.info("OrderProducer interrupted, shutting down gracefully");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in producer loop", e);
            }
        }
        logger.info("OrderProducer stopped. Total orders produced: {}", getOrdersProduced());
    }

    private long calculateDelayBetweenOrders() {
        int targetRate = applicationConfig.getOrderGenerationRate(); // orders per second

        if (targetRate <= 0) {
            logger.warn("Invalid generation rate: {}. Using default 100", targetRate);
            targetRate = 100;
        }

        // Calculate milliseconds between orders
        long delayMs = 1000L / targetRate;

        logger.info("Target rate: {} orders/sec => {}ms delay between orders",
                targetRate, delayMs);

        return delayMs;
    }

    public void stop(){
        logger.info("STOP Signal Received for OrderProducer!!");
        running=false;
    }

    public long getOrdersProduced() {
        return orderProduced.get();
    }
}
