package com.stdiscm.shareddb;

import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

/**
 * Turns database failures into gRPC statuses for every service that uses shared-db
 * (registered by {@link SharedDbAutoConfiguration}). Without it the starter reports them as UNKNOWN.
 * <ul>
 *   <li>write refused by the read-only replica (primary down)  -> UNAVAILABLE</li>
 *   <li>no database reachable at all                             -> UNAVAILABLE</li>
 *   <li>anything else                                            -> INTERNAL (logged)</li>
 * </ul>
 * The view node shows UNAVAILABLE as "this service is temporarily unavailable" on that page only.
 */
@GrpcAdvice
public class DatabaseExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(DatabaseExceptionAdvice.class);

    @GrpcExceptionHandler(DataAccessException.class)
    public Status dataAccess(DataAccessException e) {
        return toStatus(e);
    }

    @GrpcExceptionHandler(TransactionException.class)
    public Status transaction(TransactionException e) {
        return toStatus(e);
    }

    static Status toStatus(Throwable e) {
        if (DbErrors.isReadOnlyReplica(e)) {
            log.warn("write refused: primary database down, replica is read-only");
            return Status.UNAVAILABLE.withDescription(
                    "saving is paused: the primary database is down and only the read-only replica is up");
        }
        if (DbErrors.isConnectionFailure(e)) {
            if (DbErrors.isBrokenConnection(e)) ReplicaFailoverDataSource.connectionBroken();
            log.warn("database unreachable: {}", e.getMessage());
            return Status.UNAVAILABLE.withDescription("database unavailable");
        }
        log.error("database error", e);
        return Status.INTERNAL.withDescription("database error");
    }
}
