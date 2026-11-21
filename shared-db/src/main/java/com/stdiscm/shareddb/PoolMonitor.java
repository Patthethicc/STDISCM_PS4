package com.stdiscm.shareddb;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically writes the pool's live numbers, including how many connections
 * are in use (active), to the service log:
 *
 * <pre>
 * pool[course-pool] inUse=3, idle=2, total=5, waiting=0, max=10
 * </pre>
 *
 * <p>The daemon thread never keeps the JVM alive and is stopped on shutdown.
 * If another DataSource implementation is supplied, monitoring is disabled
 * instead of failing the service.
 */
public class PoolMonitor implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PoolMonitor.class);

    private final HikariDataSource hikari;
    private final ScheduledExecutorService scheduler;

    public PoolMonitor(DataSource dataSource, long intervalMs) {
        this.hikari = dataSource instanceof HikariDataSource h ? h : null;
        if (hikari == null) {
            log.warn("Pool monitor disabled: {} is not a HikariDataSource",
                    dataSource.getClass().getName());
            this.scheduler = null;
            return;
        }

        long interval = Math.max(1_000, intervalMs);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-pool-monitor");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleWithFixedDelay(this::logState, interval, interval, TimeUnit.MILLISECONDS);
    }

    /** Logs the current pool state now. Safe to call from anywhere. */
    public void logState() {
        if (hikari == null) return;
        try {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool == null) {
                log.debug("pool[{}] not started yet", hikari.getPoolName());
                return;
            }
            log.info("pool[{}] inUse={}, idle={}, total={}, waiting={}, max={}",
                    hikari.getPoolName(),
                    pool.getActiveConnections(),
                    pool.getIdleConnections(),
                    pool.getTotalConnections(),
                    pool.getThreadsAwaitingConnection(),
                    hikari.getMaximumPoolSize());
        } catch (RuntimeException e) {
            // A pool closing during shutdown must not kill the monitor thread.
            log.debug("pool[{}] state unavailable: {}", hikari.getPoolName(), e.toString());
        }
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
