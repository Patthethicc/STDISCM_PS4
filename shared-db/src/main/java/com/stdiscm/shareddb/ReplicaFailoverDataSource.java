package com.stdiscm.shareddb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Bonus 1: hands out connections from the primary database and falls back to a read-only
 * streaming replica when the primary cannot be reached ("retry on the primary" strategy).
 *
 * <ul>
 *   <li>Reads keep working while the primary is down: the replica holds every change it streamed.</li>
 *   <li>Writes on the replica fail with SQLSTATE 25006 (read-only transaction);
 *       {@link DatabaseExceptionAdvice} turns that into gRPC UNAVAILABLE with a clear message.</li>
 *   <li>After a failed attempt the primary is skipped for {@code recheckMillis}, so requests do not
 *       each pay the primary's connection timeout. Then it is tried again: automatic failback.</li>
 *   <li>A connection that breaks mid-query (the primary died after the pool handed it out) is reported
 *       through {@link #connectionBroken()}: the primary is skipped right away and its pooled
 *       connections are evicted, so the retry in {@link DbRetry} lands on the replica instead of on
 *       another dead pooled connection.</li>
 * </ul>
 *
 * Built by {@link FailoverDataSourceFactory}; each service exposes it as its only DataSource bean.
 */
public class ReplicaFailoverDataSource implements DataSource, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReplicaFailoverDataSource.class);

    /** Every open instance (normally one per service), so a broken connection can be reported without wiring. */
    private static final Set<ReplicaFailoverDataSource> OPEN = ConcurrentHashMap.newKeySet();

    private final String name;
    private final DataSource primary;
    private final DataSource replica;          // null when no replica is configured
    private final long recheckMillis;
    private final LongSupplier clock;

    private volatile long skipPrimaryUntil = 0;
    private final AtomicBoolean onReplica = new AtomicBoolean(false);

    public ReplicaFailoverDataSource(String name, DataSource primary, DataSource replica, long recheckMillis) {
        this(name, primary, replica, recheckMillis, System::currentTimeMillis);
    }

    ReplicaFailoverDataSource(String name, DataSource primary, DataSource replica, long recheckMillis,
                              LongSupplier clock) {
        if (primary == null) throw new IllegalArgumentException("primary DataSource is required");
        this.name = name;
        this.primary = primary;
        this.replica = replica;
        this.recheckMillis = recheckMillis;
        this.clock = clock;
        OPEN.add(this);
    }

    /**
     * Called when a query failed because its connection broke (SQLSTATE 08xxx / 57Pxx). Skips the primary
     * for the recheck window and evicts its pooled connections, which are likely dead too.
     */
    public static void connectionBroken() {
        for (ReplicaFailoverDataSource ds : OPEN) ds.primaryLooksDown();
    }

    void primaryLooksDown() {
        if (replica == null) return;
        skipPrimaryUntil = clock.getAsLong() + recheckMillis;
        evictPooledConnections(primary);
    }

    private static void evictPooledConnections(DataSource pool) {
        if (pool instanceof com.zaxxer.hikari.HikariDataSource h && h.getHikariPoolMXBean() != null) {
            h.getHikariPoolMXBean().softEvictConnections();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (replica == null) {
            return primary.getConnection();
        }

        SQLException primaryFailure = null;
        if (clock.getAsLong() >= skipPrimaryUntil) {
            try {
                Connection c = primary.getConnection();
                if (onReplica.compareAndSet(true, false)) {
                    log.warn("[{}] FAILBACK: primary database is reachable again, using the primary", name);
                }
                return c;
            } catch (SQLException e) {
                primaryFailure = e;
                skipPrimaryUntil = clock.getAsLong() + recheckMillis;
            }
        }

        try {
            Connection c = replica.getConnection();
            if (onReplica.compareAndSet(false, true)) {
                log.warn("[{}] FAILOVER: primary database unreachable ({}); serving from the read-only replica, "
                        + "retrying the primary every {} ms", name, describe(primaryFailure), recheckMillis);
            }
            return c;
        } catch (SQLException e) {
            if (primaryFailure != null) e.addSuppressed(primaryFailure);
            throw e;
        }
    }

    /** True while connections come from the replica (writes will be refused). */
    public boolean isUsingReplica() {
        return onReplica.get();
    }

    public boolean hasReplica() {
        return replica != null;
    }

    public DataSource primary() { return primary; }

    public DataSource replica() { return replica; }

    private static String describe(SQLException e) {
        return e == null ? "a connection broke mid-query" : e.getMessage();
    }

    @Override
    public void close() throws Exception {
        OPEN.remove(this);
        if (primary instanceof AutoCloseable p) p.close();
        if (replica instanceof AutoCloseable r) r.close();
    }

    // ---- remaining DataSource methods: not used by Spring/Hibernate here, delegated to the primary

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLFeatureNotSupportedException("credentials are fixed in the pool configuration");
    }

    @Override public PrintWriter getLogWriter() throws SQLException { return primary.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { primary.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { primary.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return primary.getLoginTimeout(); }

    @Override
    public java.util.logging.Logger getParentLogger() {
        return java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) return iface.cast(this);
        return primary.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || primary.isWrapperFor(iface);
    }
}
