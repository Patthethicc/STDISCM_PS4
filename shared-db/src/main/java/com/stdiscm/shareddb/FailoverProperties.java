package com.stdiscm.shareddb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bonus 1 settings, bound from {@code db.failover} in a service's application.yml. Failover is
 * off until {@code replica-host} is set. The primary comes from {@code spring.datasource.url},
 * and pool sizing from {@code db.pool} ({@link PoolProperties}, Bonus 3).
 *
 * <pre>
 * db:
 *   failover:
 *     replica-host: ${GRADE_DB_REPLICA_HOST:}   # empty = no replica, failover off
 *     replica-port: ${GRADE_DB_REPLICA_PORT:5432}
 * </pre>
 *
 * The timeouts are sized for the gateway's 2-second deadline. While failover is on they replace
 * {@code db.pool.connection-timeout-ms} (3 s by default), which would be longer than the deadline.
 */
@ConfigurationProperties(prefix = "db.failover")
public class FailoverProperties {

    private String replicaHost = "";
    private int replicaPort = 5432;
    /** After the primary fails, skip it for this long before trying it again (automatic failback). */
    private long recheckMs = 5_000;
    /** Wait for a pooled connection (Hikari minimum is 250). */
    private long connectionTimeoutMs = 500;
    /** Connection validity check; must be below connectionTimeoutMs (Hikari minimum is 250). */
    private long validationTimeoutMs = 300;
    /**
     * PgJDBC URL parameters, in seconds. socketTimeout=1 makes a query on a hung (not crashed)
     * database fail after 1 s instead of waiting past the gateway's 2 s deadline.
     */
    private String jdbcParams = "connectTimeout=1&socketTimeout=1&tcpKeepAlive=true";

    public boolean isEnabled() { return replicaHost != null && !replicaHost.isBlank(); }

    public String getReplicaHost() { return replicaHost; }
    public void setReplicaHost(String replicaHost) { this.replicaHost = replicaHost; }
    public int getReplicaPort() { return replicaPort; }
    public void setReplicaPort(int replicaPort) { this.replicaPort = replicaPort; }
    public long getRecheckMs() { return recheckMs; }
    public void setRecheckMs(long recheckMs) { this.recheckMs = recheckMs; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
    public long getValidationTimeoutMs() { return validationTimeoutMs; }
    public void setValidationTimeoutMs(long validationTimeoutMs) { this.validationTimeoutMs = validationTimeoutMs; }
    public String getJdbcParams() { return jdbcParams; }
    public void setJdbcParams(String jdbcParams) { this.jdbcParams = jdbcParams; }
}
