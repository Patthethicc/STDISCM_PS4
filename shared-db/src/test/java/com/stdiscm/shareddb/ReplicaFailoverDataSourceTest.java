package com.stdiscm.shareddb;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Failover logic with fake pools: no database needed. */
class ReplicaFailoverDataSourceTest {

    /** A fake pool that can be switched off and counts how often it was asked. */
    static final class FakePool {
        final AtomicBoolean up = new AtomicBoolean(true);
        final AtomicInteger calls = new AtomicInteger();
        final Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {Connection.class}, (p, m, a) -> null);
        final DataSource ds = (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {DataSource.class}, (p, m, a) -> {
                    if (m.getName().equals("getConnection")) {
                        calls.incrementAndGet();
                        if (!up.get()) throw new SQLTransientConnectionException("down", "08001");
                        return connection;
                    }
                    return null;
                });
    }

    final FakePool primary = new FakePool();
    final FakePool replica = new FakePool();
    final AtomicLong now = new AtomicLong(1_000);
    final ReplicaFailoverDataSource ds =
            new ReplicaFailoverDataSource("test", primary.ds, replica.ds, 5_000, now::get);

    @Test
    void usesPrimaryWhileItIsUp() throws SQLException {
        assertSame(primary.connection, ds.getConnection());
        assertFalse(ds.isUsingReplica());
        assertEquals(0, replica.calls.get());
    }

    @Test
    void failsOverToReplicaWhenPrimaryIsDown() throws SQLException {
        primary.up.set(false);
        assertSame(replica.connection, ds.getConnection());
        assertTrue(ds.isUsingReplica());
    }

    @Test
    void skipsPrimaryDuringRecheckWindowThenFailsBack() throws SQLException {
        primary.up.set(false);
        ds.getConnection();                       // primary tried once, fails over
        assertEquals(1, primary.calls.get());

        now.addAndGet(4_000);                     // still inside the 5 s window
        assertSame(replica.connection, ds.getConnection());
        assertEquals(1, primary.calls.get(), "primary must not be retried inside the window");

        primary.up.set(true);
        now.addAndGet(1_001);                     // window over: try the primary again
        assertSame(primary.connection, ds.getConnection());
        assertFalse(ds.isUsingReplica());
    }

    @Test
    void brokenConnectionReportSkipsThePrimaryRightAway() throws SQLException {
        ds.getConnection();                       // healthy: primary
        ReplicaFailoverDataSource.connectionBroken();   // a query died mid-flight
        assertSame(replica.connection, ds.getConnection(), "retry must go to the replica, not another pooled primary connection");
        assertEquals(1, primary.calls.get());
        now.addAndGet(5_001);
        assertSame(primary.connection, ds.getConnection(), "after the window the primary is tried again");
    }

    @Test
    void bothDownThrowsWithPrimaryFailureAttached() {
        primary.up.set(false);
        replica.up.set(false);
        SQLException e = assertThrows(SQLException.class, ds::getConnection);
        assertEquals(1, e.getSuppressed().length);
        assertTrue(DbErrors.isConnectionFailure(e));
    }

    @Test
    void withoutReplicaBehavesLikeThePrimary() {
        ReplicaFailoverDataSource single = new ReplicaFailoverDataSource("solo", primary.ds, null, 5_000, now::get);
        primary.up.set(false);
        assertThrows(SQLException.class, single::getConnection);
        assertFalse(single.hasReplica());
    }
}
