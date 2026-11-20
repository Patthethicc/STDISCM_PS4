package com.stdiscm.gateway.error;

import io.grpc.StatusRuntimeException;

/** A failed gRPC call, tagged with the service (node) it went to. */
public class ServiceCallException extends RuntimeException {

    private final String service;
    private final StatusRuntimeException grpcError;

    public ServiceCallException(String service, StatusRuntimeException grpcError) {
        super(service + ": " + grpcError.getStatus(), grpcError);
        this.service = service;
        this.grpcError = grpcError;
    }

    public String service() {
        return service;
    }

    public StatusRuntimeException grpcError() {
        return grpcError;
    }
}
