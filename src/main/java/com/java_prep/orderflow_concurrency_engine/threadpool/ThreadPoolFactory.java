package com.java_prep.orderflow_concurrency_engine.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolFactory {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolFactory.class);

    private static final int PRODUCER_POOL_SIZE= 10;
    private static final int VALIDATION_POOL_SIZE=8;
    private static final int RESERVATION_POOL_SIZE=10;
    private static final int PAYMENT_POOL_SIZE=12;
    private static final int FULFILLMENT_POOL_SIZE= 8;
    private static final String PRODUCER_POOL_NAME="producer";
    private static final String VALIDATION_POOL_NAME= "validation";
    private static final String RESERVATION_POOL_NAME= "reservation";
    private static final String PAYMENT_POOL_NAME="payment";
    private static final String FULFILLMENT_POOL_NAME= "fulfillment";
    private static final long SHUTDOWN_TIMEOUT_SECONDS= 60;

    private final Map<String, ExecutorService> threadPools;
    private final Map<String, ThreadFactory> threadFactories;
    private volatile boolean isShutdown;

    public ThreadPoolFactory(){
        this.threadPools =new ConcurrentHashMap<>();
        this.threadFactories=new ConcurrentHashMap<>();
        isShutdown=false;
        threadFactories.put(PRODUCER_POOL_NAME,createNamedThreadFactory("producer-"));
        threadFactories.put(VALIDATION_POOL_NAME,createNamedThreadFactory("validation-"));
        threadFactories.put(RESERVATION_POOL_NAME,createNamedThreadFactory("reservation-"));
        threadFactories.put(PAYMENT_POOL_NAME,createNamedThreadFactory("payment-"));
        threadFactories.put(FULFILLMENT_POOL_NAME,createNamedThreadFactory("fulfillment-"));
        registerThreadPool(PRODUCER_POOL_NAME, createProducerPool());
        registerThreadPool(VALIDATION_POOL_NAME, createValidationPool());
        registerThreadPool(RESERVATION_POOL_NAME, createReservationPool());
        registerThreadPool(PAYMENT_POOL_NAME, createPaymentPool());
        registerThreadPool(FULFILLMENT_POOL_NAME, createFulfillmentPool());
        logger.info("ThreadPoolFactory initialized with 5 threadPools");
        logger.info("Producer pool: {} threads", PRODUCER_POOL_SIZE);
        logger.info("Validation pool: {} threads", VALIDATION_POOL_SIZE);
        logger.info("Reservation pool: {} threads", RESERVATION_POOL_SIZE);
        logger.info("Payment pool: {} threads", PAYMENT_POOL_SIZE);
        logger.info("Fulfillment pool: {} threads", FULFILLMENT_POOL_SIZE);
    }

    private void registerThreadPool(String threadPoolName, ExecutorService threadPool) {
        if(threadPool ==null)
            throw new IllegalArgumentException("Pool Cannot be Null");
        if (threadPools.containsKey(threadPoolName))
            throw new IllegalArgumentException("Pool "+ threadPoolName +" already exists");
        threadPools.put(threadPoolName, threadPool);
        logger.debug("Registered Pool {}", threadPoolName);
    }

    private ExecutorService createFulfillmentPool() {
        ThreadFactory factory = threadFactories.get(FULFILLMENT_POOL_NAME);
        ExecutorService pool = new ThreadPoolExecutor(
                FULFILLMENT_POOL_SIZE,           // corePoolSize
                FULFILLMENT_POOL_SIZE,           // maximumPoolSize
                0L,                              // keepAliveTime
                TimeUnit.MILLISECONDS,           // unit
                new LinkedBlockingQueue<>(),     // workQueue (unbounded)
                factory,                         // threadFactory
                new ThreadPoolExecutor.AbortPolicy()  // handler
        );
        logger.debug("Created fulfillment pool with {} threads", FULFILLMENT_POOL_SIZE);
        return pool;
    }

    private ExecutorService createPaymentPool() {
        ThreadFactory factory = threadFactories.get(PAYMENT_POOL_NAME);

        ExecutorService pool = new ThreadPoolExecutor(
                PAYMENT_POOL_SIZE,               // corePoolSize
                PAYMENT_POOL_SIZE,               // maximumPoolSize
                0L,                              // keepAliveTime
                TimeUnit.MILLISECONDS,           // unit
                new LinkedBlockingQueue<>(),     // workQueue (unbounded)
                factory,                         // threadFactory
                new ThreadPoolExecutor.AbortPolicy()  // handler
        );
        logger.debug("Created payment pool with {} threads", PAYMENT_POOL_SIZE);
        return pool;
    }

    private ExecutorService createReservationPool() {
        ThreadFactory factory = threadFactories.get(RESERVATION_POOL_NAME);
        ExecutorService pool = new ThreadPoolExecutor(
                RESERVATION_POOL_SIZE,           // corePoolSize
                RESERVATION_POOL_SIZE,           // maximumPoolSize
                0L,                              // keepAliveTime
                TimeUnit.MILLISECONDS,           // unit
                new LinkedBlockingQueue<>(),     // workQueue (unbounded)
                factory,                         // threadFactory
                new ThreadPoolExecutor.AbortPolicy()  // handler
        );
        logger.debug("Created reservation pool with {} threads", RESERVATION_POOL_SIZE);
        return pool;
    }

    private ExecutorService createValidationPool() {
        ThreadFactory validatorFactory = threadFactories.get(VALIDATION_POOL_NAME);
        logger.debug("Created producer pool with {} threads", VALIDATION_POOL_SIZE);
        return new ThreadPoolExecutor(
                VALIDATION_POOL_SIZE,
                VALIDATION_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                validatorFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private ExecutorService createProducerPool() {
        ThreadFactory producerFactory = threadFactories.get(PRODUCER_POOL_NAME);
        logger.debug("Created producer pool with {} threads", PRODUCER_POOL_SIZE);
        return new ThreadPoolExecutor(
                PRODUCER_POOL_SIZE,
                PRODUCER_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                producerFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private ThreadFactory createNamedThreadFactory(String namePrefix) {
        AtomicInteger threadCounter = new AtomicInteger(1);
        return (Runnable runnable)->{
            String threadName=namePrefix+threadCounter.getAndIncrement();;
            Thread thread=new Thread(runnable, threadName);
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception in thread {}: {}", t.getName(), e.getMessage(), e));
            return thread;
        };
    }

    public ExecutorService getPool(String poolName){
        if (isShutdown)
            throw new IllegalStateException("ThreadPoolFactory has been shut down");
        ExecutorService threadPool = threadPools.get(poolName);
        if (threadPool == null)
            throw new IllegalArgumentException("Pool not found: " + poolName);
        return threadPool;
    }

    public Set<String> getAllPoolNames(){
        return new HashSet<>(threadPools.keySet());
    }

    public int getPoolCount() {
        return threadPools.size();
    }

    public boolean isPoolShutdown(String poolName) {
        ExecutorService pool = threadPools.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("Pool not found: " + poolName);
        }
        return pool.isShutdown();
    }

    public boolean isPoolTerminated(String poolName) {
        ExecutorService pool = threadPools.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("Pool not found: " + poolName);
        }
        return pool.isTerminated();
    }

    public boolean shutdownAll(long timeout, TimeUnit unit) throws InterruptedException {
        if (isShutdown) {
            logger.warn("ThreadPoolFactory already shut down");
            return true;
        }
        isShutdown = true;
        logger.info("Initiating shutdown of all thread pools");
        for (Map.Entry<String, ExecutorService> entry : threadPools.entrySet()) {
            String poolName = entry.getKey();
            ExecutorService pool = entry.getValue();
            logger.info("Shutting down pool: {}", poolName);
            pool.shutdown();  // Initiates orderly shutdown
        }
        long timeoutNanos = unit.toNanos(timeout);
        long startTime = System.nanoTime();
        boolean allTerminated = true;

        for (Map.Entry<String, ExecutorService> entry : threadPools.entrySet()) {
            String poolName = entry.getKey();
            ExecutorService pool = entry.getValue();
            long elapsedNanos = System.nanoTime() - startTime;
            long remainingNanos = timeoutNanos - elapsedNanos;
            if (remainingNanos <= 0) {
                logger.warn("Timeout expired before pool {} could terminate", poolName);
                allTerminated = false;
                continue;
            }
            boolean terminated = pool.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
            if (terminated) {
                logger.info("Pool {} terminated successfully", poolName);
            } else {
                logger.warn("Pool {} did not terminate within timeout", poolName);
                allTerminated = false;
            }
        }
        if (!allTerminated) {
            logger.warn("Forcing shutdown of non-terminated pools");
            for (Map.Entry<String, ExecutorService> entry : threadPools.entrySet()) {
                ExecutorService pool = entry.getValue();
                if (!pool.isTerminated()) {
                    List<Runnable> droppedTasks = pool.shutdownNow();
                    logger.error("Force shutdown pool {}, dropped {} tasks",
                            entry.getKey(), droppedTasks.size());
                }
            }
        }
        if (allTerminated) {
            logger.info("All thread pools shut down successfully");
        } else {
            logger.error("Some thread pools did not terminate gracefully");
        }
        return allTerminated;
    }

    public boolean shutdownAll() throws InterruptedException {
        return shutdownAll(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
