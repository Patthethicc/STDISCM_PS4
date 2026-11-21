package com.stdiscm.shareddb;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbErrorsTest {

    /** Spring and Hibernate wrap the driver error a few levels deep. */
    static RuntimeException wrapped(Throwable root) {
        return new RuntimeException("spring", new IllegalStateException("hibernate", root));
    }

    @Test
    void readOnlyReplicaIsRecognisedThroughWrappers() {
        Throwable e = wrapped(new SQLException("cannot execute INSERT in a read-only transaction", "25006"));
        assertTrue(DbErrors.isReadOnlyReplica(e));
        assertFalse(DbErrors.isConnectionFailure(e));
    }

    @Test
    void connectionFailuresAreRecognised() {
        assertTrue(DbErrors.isConnectionFailure(wrapped(new SQLException("refused", "08001"))));
        assertTrue(DbErrors.isConnectionFailure(wrapped(new SQLException("terminating connection", "57P01"))));
        assertTrue(DbErrors.isConnectionFailure(wrapped(new ConnectException("Connection refused"))));
        SQLException chained = new SQLException("batch failed", "XX000");
        chained.setNextException(new SQLException("I/O error", "08006"));
        assertTrue(DbErrors.isConnectionFailure(wrapped(chained)));
    }

    @Test
    void brokenConnectionIsOnlyServerOrLinkFailure() {
        assertTrue(DbErrors.isBrokenConnection(wrapped(new SQLException("terminating connection", "57P01"))));
        assertTrue(DbErrors.isBrokenConnection(wrapped(new SQLException("I/O error", "08006"))));
        assertFalse(DbErrors.isBrokenConnection(wrapped(new java.sql.SQLTransientConnectionException("pool timeout"))));
        assertFalse(DbErrors.isBrokenConnection(wrapped(new SQLException("read only", "25006"))));
    }

    @Test
    void ordinaryErrorsAreNeither() {
        Throwable e = wrapped(new SQLException("duplicate key", "23505"));
        assertFalse(DbErrors.isReadOnlyReplica(e));
        assertFalse(DbErrors.isConnectionFailure(e));
        assertFalse(DbErrors.isConnectionFailure(null));
    }
}
