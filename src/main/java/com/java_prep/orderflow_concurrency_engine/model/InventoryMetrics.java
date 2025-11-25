package com.java_prep.orderflow_concurrency_engine.model;

public class InventoryMetrics {

    private final long totalAttempts;
    private final long successes;
    private final long failures;
    private final long releases;
    private final long contentions;
    private final double successRate;

    public InventoryMetrics(long totalAttempts, long successes, long failures,
                            long releases, long contentions, double successRate) {
        this.totalAttempts = totalAttempts;
        this.successes = successes;
        this.failures = failures;
        this.releases = releases;
        this.contentions = contentions;
        this.successRate = successRate;
    }

    public long getTotalAttempts() {
        return totalAttempts;
    }

    public long getSuccesses() {
        return successes;
    }

    public long getFailures() {
        return failures;
    }

    public long getReleases() {
        return releases;
    }

    public long getContentions() {
        return contentions;
    }

    public double getSuccessRate() {
        return successRate;
    }

    @Override
    public String toString() {
        return String.format(
                "InventoryMetrics{Attempts: %d | Successes: %d | Failures: %d | Releases: %d | Success Rate: %.2f%% | Contentions: %d}",
                totalAttempts, successes, failures, releases, successRate, contentions
        );
    }
}
