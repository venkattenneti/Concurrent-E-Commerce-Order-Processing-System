package com.java_prep.orderflow_concurrency_engine.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadPoolMonitor {
    private static final Logger logger= LoggerFactory.getLogger(ThreadPoolFactory.class);

    private static final long DEFAULT_MONITORING_INTERVAL_SECONDS=5;
    private static final int METRICS_HISTORY_LIMIT=100;
    private static final double HIGH_UTILIZATION_THRESHOLD= 95.0;
    private static final int HIGH_QUEUE_DEPTH_THRESHOLD= 1000;
    private static final String METRIC_TABLE_SEPARATOR= "=";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ScheduledExecutorService monitorScheduler;
    private final ConcurrentHashMap<String, List<PoolMetrics>> metricsHistory;
    private final ConcurrentHashMap<String,PoolMetrics> currentMetrics;
    private final ThreadPoolFactory threadPoolFactory;
    private final AtomicLong totalTasksProcessed;
    private long monitoringStartTime;
    private volatile boolean isMonitoring;

    public static class PoolMetrics {
        private final String poolName;
        private final int activeCount;
        private final int poolSize;
        private final int corePoolSize;
        private final int maximumPoolSize;
        private final int queueSize;
        private final long completedTaskCount;
        private final long taskCount;
        private final double utilizationRate;
        private final double queueDepthRatio;
        private final LocalDateTime timestamp;

        public PoolMetrics(String poolName, int activeCount, int poolSize,
                           int corePoolSize, int maximumPoolSize, int queueSize,
                           long completedTaskCount, long taskCount,
                           double utilizationRate, double queueDepthRatio) {
            this.poolName = poolName;
            this.activeCount = activeCount;
            this.poolSize = poolSize;
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
            this.queueSize = queueSize;
            this.completedTaskCount = completedTaskCount;
            this.taskCount = taskCount;
            this.utilizationRate = utilizationRate;
            this.queueDepthRatio = queueDepthRatio;
            this.timestamp = LocalDateTime.now();
        }

        public String getPoolName() {
            return poolName;
        }
        public int getActiveCount() {
            return activeCount;
        }
        public int getPoolSize() {
            return poolSize;
        }
        public int getCorePoolSize() {
            return corePoolSize;
        }
        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }
        public int getQueueSize() {
            return queueSize;
        }
        public long getCompletedTaskCount() {
            return completedTaskCount;
        }
        public long getTaskCount() {
            return taskCount;
        }
        public double getUtilizationRate() {
            return utilizationRate;
        }
        public double getQueueDepthRatio() {
            return queueDepthRatio;
        }
        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "PoolMetrics{" +
                    "poolName='" + poolName + '\'' +
                    ", activeCount=" + activeCount +
                    ", poolSize=" + poolSize +
                    ", corePoolSize=" + corePoolSize +
                    ", maximumPoolSize=" + maximumPoolSize +
                    ", queueSize=" + queueSize +
                    ", completedTaskCount=" + completedTaskCount +
                    ", taskCount=" + taskCount +
                    ", utilizationRate=" + utilizationRate +
                    ", queueDepthRatio=" + queueDepthRatio +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
    public ThreadPoolMonitor(ThreadPoolFactory threadPoolFactory){
        if(threadPoolFactory==null)
            throw new IllegalArgumentException("Thread Pool Cannot be null");
        this.threadPoolFactory = threadPoolFactory;
        this.metricsHistory = new ConcurrentHashMap<>();
        this.currentMetrics = new ConcurrentHashMap<>();
        this.totalTasksProcessed = new AtomicLong(0);
        this.isMonitoring = false;
        this.monitorScheduler = null;
        this.monitoringStartTime = 0L;
        for (String poolName : threadPoolFactory.getAllPoolNames()) {
            metricsHistory.put(poolName, new ArrayList<>());
        }
        logger.info("ThreadPoolMonitor initialized for {} pools", threadPoolFactory.getPoolCount());
    }

    public void startMonitoring(long interval, TimeUnit unit){
        if (isMonitoring)
            throw new IllegalStateException("Monitoring already active!!");
        monitorScheduler = Executors.newSingleThreadScheduledExecutor(r->{
            Thread thread= new Thread(r,"ppol-monitor-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        monitoringStartTime = System.currentTimeMillis();
        isMonitoring = true;
        monitorScheduler.scheduleAtFixedRate(
                this::collectAndLogMetrics,  // Task to run
                0,                           // Initial delay (start immediately)
                interval,                    // Period between executions
                unit                         // Time unit
        );
    }

    public void startMonitoring() {
        startMonitoring(DEFAULT_MONITORING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stopMonitoring() {
        if (!isMonitoring)
            throw new IllegalStateException("Monitoring not active!!");
        isMonitoring = false;
        if (monitorScheduler != null) {
            monitorScheduler.shutdown();
            try {
                if (!monitorScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("Monitor scheduler did not terminate, forcing shutdown");
                    monitorScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                monitorScheduler.shutdownNow();
                logger.error("Interrupted while stopping monitor", e);
            }
        }
        logger.info("Collecting final metrics before shutdown");
        collectMetrics();
        printMetricsSummary();
        printFinalReport();
        logger.info("Thread pool monitoring stopped");
    }

    public Map<String, PoolMetrics> collectMetrics() {
        Map<String, PoolMetrics> snapshot = new ConcurrentHashMap<>();
        long snapshotTaskTotal = 0;

        for (String poolName : threadPoolFactory.getAllPoolNames()) {
            try {
                ExecutorService pool = threadPoolFactory.getPool(poolName);
                PoolMetrics metrics = collectPoolMetrics(pool, poolName);
                snapshot.put(poolName, metrics);
                currentMetrics.put(poolName, metrics);
                updateMetricsHistory(poolName, metrics);
                snapshotTaskTotal += metrics.getCompletedTaskCount();
            } catch (Exception e) {
                logger.error("Error collecting metrics for pool {}: {}", poolName, e.getMessage());
            }
        }
        totalTasksProcessed.set(snapshotTaskTotal);
        return snapshot;
    }
    private PoolMetrics collectPoolMetrics(ExecutorService pool, String poolName) {
        if (!(pool instanceof ThreadPoolExecutor)) {
            logger.warn("Pool {} is not a ThreadPoolExecutor, returning empty metrics", poolName);
            return createEmptyMetrics(poolName);
        }
        ThreadPoolExecutor executor = (ThreadPoolExecutor) pool;
        int activeCount = executor.getActiveCount();
        int poolSize = executor.getPoolSize();
        int corePoolSize = executor.getCorePoolSize();
        int maximumPoolSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();
        long completedTaskCount = executor.getCompletedTaskCount();
        long taskCount = executor.getTaskCount();
        double utilizationRate = calculateUtilization(activeCount, poolSize);
        double queueDepthRatio = calculateQueueDepthRatio(queueSize, completedTaskCount);
        return new PoolMetrics(poolName, activeCount, poolSize, corePoolSize, maximumPoolSize,
                queueSize, completedTaskCount, taskCount, utilizationRate, queueDepthRatio
        );
    }
    private PoolMetrics createEmptyMetrics(String poolName) {
        return new PoolMetrics(poolName, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0);
    }

    private double calculateUtilization(int active, int size) {
        if (size == 0) {
            return 0.0;
        }
        return (active * 100.0) / size;
    }

    private double calculateQueueDepthRatio(int queueSize, long completed) {
        long total = queueSize + completed;
        if (total == 0) {
            return 0.0;
        }
        return (queueSize * 1.0) / total;
    }

    private void updateMetricsHistory(String poolName, PoolMetrics metrics) {
        List<PoolMetrics> history = metricsHistory.computeIfAbsent(
                poolName,
                k -> Collections.synchronizedList(new ArrayList<>())
        );
        synchronized (history) {
            history.add(metrics);
            if (history.size() > METRICS_HISTORY_LIMIT) {
                history.remove(0);
            }
        }
    }

    public PoolMetrics getPoolMetrics(String poolName) {
        PoolMetrics metrics = currentMetrics.get(poolName);
        if (metrics == null) {
            throw new IllegalArgumentException("No metrics found for pool: " + poolName);
        }
        return metrics;
    }

    public double getUtilizationRate(String poolName) {
        PoolMetrics metrics = getPoolMetrics(poolName);
        return metrics.getUtilizationRate();
    }

    public List<PoolMetrics> getMetricsHistory(String poolName) {
        List<PoolMetrics> history = metricsHistory.get(poolName);
        if (history == null) {
            return new ArrayList<>();
        }
        synchronized (history) {
            return new ArrayList<>(history);  // Return copy
        }
    }

    private void collectAndLogMetrics() {
        try {
            collectMetrics();
            printMetricsSummary();
            List<String> bottlenecks = identifyBottlenecks();
            if (!bottlenecks.isEmpty()) {
                logger.warn("=== BOTTLENECKS DETECTED ===");
                for (String bottleneck : bottlenecks) {
                    logger.warn("  {}", bottleneck);
                }
            }
        } catch (Exception e) {
            logger.error("Error during metric collection: {}", e.getMessage(), e);
        }
    }

    public void printMetricsSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    THREAD POOL METRICS SUMMARY");
        System.out.println("=".repeat(80));
        System.out.println("[Timestamp: " + LocalDateTime.now().format(TIME_FORMATTER) + "]");
        System.out.println();
        System.out.printf("%-13s | %-8s | %-6s | %-7s | %-11s | %-12s%n",
                "Pool Name", "Active", "Size", "Queue", "Completed", "Utilization");
        System.out.println("-".repeat(80));
        for (String poolName : threadPoolFactory.getAllPoolNames()) {
            PoolMetrics metrics = currentMetrics.get(poolName);
            if (metrics != null) {
                System.out.println(formatMetricRow(metrics));
            }
        }
        System.out.println();
        System.out.println("SYSTEM TOTALS:");
        System.out.printf("  Total Tasks Processed: %,d%n", totalTasksProcessed.get());
        if (monitoringStartTime > 0) {
            long uptimeMillis = System.currentTimeMillis() - monitoringStartTime;
            double uptimeSeconds = uptimeMillis / 1000.0;
            System.out.printf("  System Uptime: %.1f seconds%n", uptimeSeconds);
            if (uptimeSeconds > 0) {
                double throughput = totalTasksProcessed.get() / uptimeSeconds;
                System.out.printf("  Average Throughput: %,.0f tasks/sec%n", throughput);
            }
        }
        System.out.println("=".repeat(80) + "\n");
    }

    private String formatMetricRow(PoolMetrics metrics) {
        return String.format("%-13s | %3d/%-3d  | %-6d | %-7d | %,11d | %8.1f%%",
                metrics.getPoolName(),
                metrics.getActiveCount(),
                metrics.getPoolSize(),
                metrics.getQueueSize(),
                metrics.getCompletedTaskCount(),
                metrics.getUtilizationRate()
        );
    }

    public List<String> identifyBottlenecks() {
        List<String> bottlenecks = new ArrayList<>();

        for (String poolName : threadPoolFactory.getAllPoolNames()) {
            PoolMetrics metrics = currentMetrics.get(poolName);
            if (metrics == null) continue;
            if (metrics.getUtilizationRate() > HIGH_UTILIZATION_THRESHOLD) {
                bottlenecks.add(String.format(
                        "Pool '%s' HIGH UTILIZATION: %.1f%% (threshold: %.1f%%)",
                        poolName, metrics.getUtilizationRate(), HIGH_UTILIZATION_THRESHOLD
                ));
            }
            if (metrics.getQueueSize() > HIGH_QUEUE_DEPTH_THRESHOLD) {
                bottlenecks.add(String.format(
                        "Pool '%s' HIGH QUEUE DEPTH: %,d tasks (threshold: %,d)",
                        poolName, metrics.getQueueSize(), HIGH_QUEUE_DEPTH_THRESHOLD
                ));
            }
            if (metrics.getActiveCount() == 0 && metrics.getQueueSize() > 0) {
                bottlenecks.add(String.format(
                        "Pool '%s' STARVATION: No active threads but %,d tasks queued",
                        poolName, metrics.getQueueSize()
                ));
            }
        }
        return bottlenecks;
    }

    private void printFinalReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    FINAL MONITORING REPORT");
        System.out.println("=".repeat(80));
        long uptimeMillis = System.currentTimeMillis() - monitoringStartTime;
        double uptimeSeconds = uptimeMillis / 1000.0;
        System.out.printf("Monitoring Duration: %.2f seconds%n", uptimeSeconds);
        System.out.printf("Total Tasks Processed: %,d%n", totalTasksProcessed.get());
        if (uptimeSeconds > 0) {
            System.out.printf("Overall Throughput: %,.0f tasks/sec%n",
                    totalTasksProcessed.get() / uptimeSeconds);
        }
        System.out.println("\nPer-Pool Summary:");
        for (String poolName : threadPoolFactory.getAllPoolNames()) {
            PoolMetrics metrics = currentMetrics.get(poolName);
            if (metrics != null) {
                System.out.printf("  %-13s: %,11d tasks completed (avg util: %.1f%%)%n",
                        metrics.getPoolName(),
                        metrics.getCompletedTaskCount(),
                        metrics.getUtilizationRate()
                );
            }
        }
        System.out.println(METRIC_TABLE_SEPARATOR.repeat(80) + "\n");
    }

}
