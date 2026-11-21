package com.stdiscm.shareddb;

import com.zaxxer.hikari.metrics.MetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional per-checkout logging. Enable with {@code db.pool.log-checkouts=true}
 * when recording the concurrency demo: every borrow prints the live in-use count,
 * so the peak is visible even if it lasts less than one monitor interval.
 */
final class PoolMetricsLogger implements MetricsTrackerFactory {

    private static final Logger log = LoggerFactory.getLogger(PoolMetricsLogger.class);

    private final boolean enabled;

    PoolMetricsLogger(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public MetricsTracker create(String poolName, PoolStats poolStats) {
        return new MetricsTracker() {
            @Override
            public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
                if (enabled) {
                    log.info("pool[{}] connection checked out: inUse={}, idle={}, total={}, waiting={}, max={}",
                            poolName,
                            poolStats.getActiveConnections(),
                            poolStats.getIdleConnections(),
                            poolStats.getTotalConnections(),
                            poolStats.getPendingThreads(),
                            poolStats.getMaxConnections());
                }
            }

            @Override
            public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
                // Usage time is available from Hikari's metrics if a service wants it later.
            }

            @Override
            public void recordConnectionTimeout() {
                log.warn("pool[{}] timed out waiting for a connection: inUse={}, total={}, waiting={}, max={}",
                        poolName,
                        poolStats.getActiveConnections(),
                        poolStats.getTotalConnections(),
                        poolStats.getPendingThreads(),
                        poolStats.getMaxConnections());
            }

            @Override
            public void close() {
                // Nothing to release.
            }
        };
    }
}
