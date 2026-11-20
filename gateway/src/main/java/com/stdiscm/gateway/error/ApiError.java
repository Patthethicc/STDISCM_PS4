package com.stdiscm.gateway.error;

/**
 * Error body sent to the React app.
 * {@code error} is a code the UI can switch on (e.g. SERVICE_UNAVAILABLE, FULL, CLOSED,
 * ALREADY_ENROLLED); {@code service} says which node failed, when known.
 */
public record ApiError(String error, String message, String service) { }
