package com.stdiscm.shareddb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Bonus 1 on top of Bonus 3: builds one pool for the primary and one for the replica with the same
 * settings {@link PoolConfig} uses for a single pool (size, idle timeout, lifetime, usage logging),
 * then wraps both in {@link ReplicaFailoverDataSource}. Only the timeouts differ, see
 * {@link FailoverProperties}.
 */
final class FailoverDataSourceFactory {

    private FailoverDataSourceFactory() { }

    static ReplicaFailoverDataSource create(String url, String username, String password,
                                            String driverClassName, PoolProperties pool,
                                            FailoverProperties failover) {
        HikariDataSource primary = pool(withParams(url, failover.getJdbcParams()), username, password,
                driverClassName, pool, failover, pool.getName() + "-primary", false);
        HikariDataSource replica = failover.isEnabled()
                ? pool(withParams(swapHost(url, failover.getReplicaHost(), failover.getReplicaPort()),
                        failover.getJdbcParams()),
                        username, password, driverClassName, pool, failover, pool.getName() + "-replica", true)
                : null;
        return new ReplicaFailoverDataSource(pool.getName(), primary, replica, failover.getRecheckMs());
    }

    private static HikariDataSource pool(String url, String username, String password, String driverClassName,
                                         PoolProperties pool, FailoverProperties failover,
                                         String poolName, boolean readOnly) {
        HikariConfig c = new PoolConfig().buildConfig(url, username, password, driverClassName, pool);
        c.setPoolName(poolName);
        c.setConnectionTimeout(failover.getConnectionTimeoutMs());
        c.setValidationTimeout(failover.getValidationTimeoutMs());
        // Start even when this database is down right now (the default, 1, fails startup instead).
        c.setInitializationFailTimeout(-1);
        c.setReadOnly(readOnly);
        return new HikariDataSource(c);
    }

    /** jdbc:postgresql://db:5432/x -> jdbc:postgresql://replica:5433/x (single-host URLs only). */
    static String swapHost(String url, String host, int port) {
        return url.replaceFirst("//[^/?]+", "//" + host + ":" + port);
    }

    static String withParams(String url, String params) {
        if (params == null || params.isBlank()) return url;
        return url + (url.contains("?") ? "&" : "?") + params;
    }
}
