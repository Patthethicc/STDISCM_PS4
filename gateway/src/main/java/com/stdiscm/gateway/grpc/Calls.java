package com.stdiscm.gateway.grpc;

import com.stdiscm.gateway.error.ServiceCallException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

import java.util.function.Supplier;

/** Helpers shared by the service clients. */
public final class Calls {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private Calls() { }

    /** Attaches "authorization: Bearer <token>" to every call made with the returned stub. */
    public static <S extends AbstractStub<S>> S withToken(S stub, String token) {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, "Bearer " + token);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    /** Runs a call and tags any failure with the service it came from. */
    public static <T> T run(ServiceChannel service, Supplier<T> call) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                service.retrySoon();
            }
            throw new ServiceCallException(service.name(), e);
        }
    }
}
