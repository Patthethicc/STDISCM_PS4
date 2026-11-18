package com.stdiscm.sharedauth;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

/**
 * The Spring gRPC starter turns any exception thrown from a service method into UNKNOWN.
 * {@link RoleGuard} and {@link AuthContext#require()} throw StatusRuntimeException, so pass
 * those through with their real status (PERMISSION_DENIED, UNAUTHENTICATED, ...).
 */
@GrpcAdvice
public class StatusExceptionAdvice {

    @GrpcExceptionHandler(StatusRuntimeException.class)
    public Status handle(StatusRuntimeException e) {
        return e.getStatus();
    }

    @GrpcExceptionHandler(StatusException.class)
    public Status handle(StatusException e) {
        return e.getStatus();
    }
}
