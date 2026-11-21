package com.stdiscm.shareddb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Explicit PostgreSQL connection-pool settings, shared by every service.
 *
 * <p>Defaults match the old per-service {@code spring.datasource.hikari} blocks:
 * 10 connections maximum, 2 kept idle, a 60-second idle timeout and a 3-second
 * wait for a connection. Override any value with environment variables in the
 * service's {@code application.yml} (see {@code db.pool}).
 */
@ConfigurationProperties(prefix = "db.pool")
public class PoolProperties {

    /** Name shown in Hikari logs and in the pool usage lines. */
    private String name = "shared-pool";

    /** Hard upper limit on connections this process may hold. */
    private int maximumPoolSize = 10;

    /** Connections kept warm so a normal request does not pay the connect cost. */
    private int minimumIdle = 2;

    /** Close a connection after this long idle (Hikari minimum: 10s). */
    private long idleTimeoutMs = 60_000;

    /** How long a caller waits for a free connection before failing. */
    private long connectionTimeoutMs = 3_000;

    /** Recycle connections periodically so long-lived pools do not go stale. */
    private long maxLifetimeMs = 1_800_000;

    /** Optional leak warning: log a stack trace if a connection is held this long. 0 = off. */
    private long leakDetectionThresholdMs = 0;

    /** How often the monitor logs active/idle/total connections. */
    private long logIntervalMs = 10_000;

    /** Log a line on every connection checkout (useful for the load-test demo). */
    private boolean logCheckouts = false;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }

    public int getMinimumIdle() { return minimumIdle; }
    public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }

    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public void setIdleTimeoutMs(long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }

    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public long getMaxLifetimeMs() { return maxLifetimeMs; }
    public void setMaxLifetimeMs(long maxLifetimeMs) { this.maxLifetimeMs = maxLifetimeMs; }

    public long getLeakDetectionThresholdMs() { return leakDetectionThresholdMs; }
    public void setLeakDetectionThresholdMs(long leakDetectionThresholdMs) {
        this.leakDetectionThresholdMs = leakDetectionThresholdMs;
    }

    public long getLogIntervalMs() { return logIntervalMs; }
    public void setLogIntervalMs(long logIntervalMs) { this.logIntervalMs = logIntervalMs; }

    public boolean isLogCheckouts() { return logCheckouts; }
    public void setLogCheckouts(boolean logCheckouts) { this.logCheckouts = logCheckouts; }
}
