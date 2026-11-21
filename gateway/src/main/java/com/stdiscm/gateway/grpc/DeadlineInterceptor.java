package com.stdiscm.gateway.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

import java.util.concurrent.TimeUnit;

/** Gives every call a deadline (2 s by default) so a down node can't hang a request. */
public class DeadlineInterceptor implements ClientInterceptor {

    private final long deadlineMs;

    public DeadlineInterceptor(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
        if (options.getDeadline() == null) {
            options = options.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
        }
        return next.newCall(method, options);
    }
}
