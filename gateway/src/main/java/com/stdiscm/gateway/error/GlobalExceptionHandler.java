package com.stdiscm.gateway.error;

import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns gRPC errors into HTTP responses. A down node (UNAVAILABLE or DEADLINE_EXCEEDED)
 * becomes 503 SERVICE_UNAVAILABLE for that request only; the rest of the app keeps working.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceCallException.class)
    public ResponseEntity<ApiError> serviceError(ServiceCallException e) {
        Status status = e.grpcError().getStatus();
        String desc = status.getDescription() == null ? "" : status.getDescription();
        String service = e.service();

        return switch (status.getCode()) {
            case UNAVAILABLE, DEADLINE_EXCEEDED -> {
                log.warn("{} service unreachable: {}", service, status.getCode());
                yield body(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                        "The " + service + " service is temporarily unavailable.", service);
            }
            case UNAUTHENTICATED -> body(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", orDefault(desc, "Please log in again."), service);
            case PERMISSION_DENIED -> body(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", orDefault(desc, "You can't do that."), service);
            case NOT_FOUND -> body(HttpStatus.NOT_FOUND, "NOT_FOUND", orDefault(desc, "Not found."), service);
            case INVALID_ARGUMENT -> body(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", orDefault(desc, "Invalid input."), service);
            // Business rule failures, e.g. FULL / CLOSED / ALREADY_ENROLLED. The service puts the code in the description.
            case ALREADY_EXISTS, FAILED_PRECONDITION -> body(HttpStatus.CONFLICT, code(desc, status), friendly(desc), service);
            default -> {
                log.error("{} service error", service, e);
                yield body(HttpStatus.BAD_GATEWAY, status.getCode().name(), "Something went wrong.", service);
            }
        };
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> unauthorized(UnauthorizedException e) {
        return body(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please log in.", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Please fill in all required fields.", null);
    }

    private static ResponseEntity<ApiError> body(HttpStatus http, String error, String message, String service) {
        return ResponseEntity.status(http).body(new ApiError(error, message, service));
    }

    private static String orDefault(String s, String fallback) {
        return s.isBlank() ? fallback : s;
    }

    /** "FULL" -> FULL; anything else -> the gRPC code name. */
    private static String code(String desc, Status status) {
        return desc.matches("[A-Z_]+") ? desc : status.getCode().name();
    }

    private static String friendly(String desc) {
        return switch (desc) {
            case "FULL" -> "This course is full.";
            case "CLOSED" -> "This course is closed for enrollment.";
            case "ALREADY_ENROLLED" -> "You're already enrolled in this course.";
            default -> orDefault(desc, "Request could not be completed.");
        };
    }
}
