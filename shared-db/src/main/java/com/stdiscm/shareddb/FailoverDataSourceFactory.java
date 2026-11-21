package com.stdiscm.shareddb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Builds a service's DataSource: an explicit Hikari pool for the primary, another for the
 * replica (when {@code db.replica.host} is set), wrapped in {@link ReplicaFailoverDataSource}.
 *
 * <pre>
 * &#64;Bean(destroyMethod = "close")
 * public ReplicaFailoverDataSource dataSource(DbSettings db) {
 *     return FailoverDataSourceFactory.create("grade-service", db);
 * }
 * </pre>
 * Note for Bonus 3: pool sizing lives in {@link #pool}; PoolConfig can take it over, but keep
 * the short timeouts and {@code initializationFailTimeout = -1}.
 */
public final class FailoverDataSourceFactory {

    private FailoverDataSourceFactory() { }

    public static ReplicaFailoverDataSource create(String serviceName, DbSettings db) {
        HikariDataSource primary = pool(serviceName + "-primary", serviceName, db, db.getPrimary(), false);
        HikariDataSource replica = db.getReplica().isConfigured()
                ? pool(serviceName + "-replica", serviceName, db, db.getReplica(), true)
                : null;
        return new ReplicaFailoverDataSource(serviceName, primary, replica, db.getFailover().getRecheckMs());
    }

    static String jdbcUrl(DbSettings db, DbSettings.Endpoint e, String applicationName) {
        String params = db.getJdbcParams() == null || db.getJdbcParams().isBlank() ? "" : db.getJdbcParams() + "&";
        return "jdbc:postgresql://" + e.getHost() + ":" + e.getPort() + "/" + db.getName()
                + "?" + params + "ApplicationName=" + applicationName;
    }

    static HikariDataSource pool(String poolName, String serviceName, DbSettings db,
                                 DbSettings.Endpoint endpoint, boolean readOnly) {
        DbSettings.Pool p = db.getPool();
        HikariConfig c = new HikariConfig();
        c.setPoolName(poolName);
        c.setJdbcUrl(jdbcUrl(db, endpoint, serviceName));
        c.setUsername(db.getUser());
        c.setPassword(db.getPassword());
        c.setMaximumPoolSize(p.getMaximumPoolSize());
        c.setMinimumIdle(p.getMinimumIdle());
        c.setIdleTimeout(p.getIdleTimeoutMs());
        c.setConnectionTimeout(p.getConnectionTimeoutMs());
        c.setValidationTimeout(p.getValidationTimeoutMs());
        c.setMaxLifetime(p.getMaxLifetimeMs());
        // Start even when this database is down right now (the default, 1, fails startup instead).
        c.setInitializationFailTimeout(-1);
        c.setReadOnly(readOnly);
        return new HikariDataSource(c);
    }
}
