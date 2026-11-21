package com.stdiscm.shareddb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * One retry for READS when the connection broke mid-query (the primary died while the request was
 * running). The retry asks {@link ReplicaFailoverDataSource} for a new connection, which by then
 * comes from the replica, so the student sees their grades instead of an error.
 * Call it OUTSIDE the transaction (from the gRPC layer), so the retry gets a fresh transaction.
 * Never wrap writes: a write cannot succeed on the read-only replica anyway.
 */
public final class DbRetry {

    private static final Logger log = LoggerFactory.getLogger(DbRetry.class);

    private DbRetry() { }

    public static <T> T read(Supplier<T> query) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            if (!DbErrors.isConnectionFailure(e)) throw e;
            if (DbErrors.isBrokenConnection(e)) ReplicaFailoverDataSource.connectionBroken();
            log.warn("read failed on a broken connection ({}), retrying once", e.getMessage());
            return query.get();
        }
    }
}
