package com.stdiscm.gateway.grpc;

import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/** A named connection to one service node. */
public class ServiceChannel {

    private final String name;
    private final ManagedChannel managed;
    private final Channel channel;

    public ServiceChannel(String name, String target, DeadlineInterceptor deadline) {
        this.name = name;
        this.managed = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .build();
        this.channel = ClientInterceptors.intercept(managed, deadline);
    }

    public String name() {
        return name;
    }

    public Channel channel() {
        return channel;
    }

    /**
     * After a node was down, gRPC waits longer and longer between reconnect attempts
     * (up to 2 minutes). Calling this when a call fails with UNAVAILABLE makes the next
     * request retry right away, so a restarted node is picked up within seconds.
     */
    public void retrySoon() {
        managed.resetConnectBackoff();
    }

    public void shutdown() {
        managed.shutdownNow();
    }
}
