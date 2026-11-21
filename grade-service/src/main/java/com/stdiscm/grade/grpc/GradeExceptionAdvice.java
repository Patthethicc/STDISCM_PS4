package com.stdiscm.grade.grpc;

import com.stdiscm.grade.service.InvalidRequestException;
import com.stdiscm.grade.service.NotTeachingException;
import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

/** Grade-specific errors -> gRPC statuses. Database errors are handled by shared-db. */
@GrpcAdvice
public class GradeExceptionAdvice {

    @GrpcExceptionHandler(InvalidRequestException.class)
    public Status invalid(InvalidRequestException e) {
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
    }

    @GrpcExceptionHandler(NotTeachingException.class)
    public Status notTeaching(NotTeachingException e) {
        return Status.PERMISSION_DENIED.withDescription(e.getMessage());
    }
}
