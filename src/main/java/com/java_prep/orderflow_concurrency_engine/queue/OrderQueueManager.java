package com.java_prep.orderflow_concurrency_engine.queue;

import com.java_prep.orderflow_concurrency_engine.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class OrderQueueManager {
    private static Logger logger= LoggerFactory.getLogger(OrderQueueManager.class);

    private static final int DEFAULT_QUEUE_CAPACITY=1000;
    private static final int WARNING_THRESHOLD_PERCENTAGE=80;
    private final BlockingQueue<Order> orderBlockingQueue;
    private final int defaultQueueCapacity;

    public OrderQueueManager(){
        this(DEFAULT_QUEUE_CAPACITY);
    }

    public OrderQueueManager(int queueCapacity) {
        if (queueCapacity<=0)
            throw new IllegalArgumentException("Capacity cannot be zero/negative");
        this.defaultQueueCapacity=queueCapacity;
        this.orderBlockingQueue=new LinkedBlockingQueue<>(defaultQueueCapacity);
        logger.debug("Blocking Queue Initialized with capacity:{}",defaultQueueCapacity);
    }

    public BlockingQueue<Order> getQueue(){
        return orderBlockingQueue;
    }

    public int getQueueSize(){
        return orderBlockingQueue.size();
    }

    public int getRemainingCapacity(){
        return orderBlockingQueue.remainingCapacity();
    }

    public double getUtilizationPercentage(){
        return ((double) getQueueSize() /defaultQueueCapacity)*100.0;
    }

    public boolean isNearCapacity(){
        return getUtilizationPercentage()>=WARNING_THRESHOLD_PERCENTAGE;
    }

    public boolean isEmpty() {
        return orderBlockingQueue.isEmpty();
    }

    public int getCapacity() {
        return defaultQueueCapacity;
    }

    public void logQueueStatus(){
        int currentQueueSize=getQueueSize();
        int remainingCapacity=getRemainingCapacity();
        double utilizationPercentage= getUtilizationPercentage();

        if(isNearCapacity()){
            logger.debug("Max capacity reaching for Queue: CurrentQueueSize:{}," +
                    "Remaining Capacity:{}," +
                    "Utilization Percentage:{}",currentQueueSize,remainingCapacity,utilizationPercentage);
        }else
            logger.debug("Current Queue stats: CurrentQueueSize:{}," +
                    "Remaining Capacity:{}," +
                    "Utilization Percentage:{}",currentQueueSize,remainingCapacity,utilizationPercentage);
    }

}
