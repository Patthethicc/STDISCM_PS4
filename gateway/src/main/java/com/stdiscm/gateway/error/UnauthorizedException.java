package com.stdiscm.gateway.error;

/** No valid session cookie: the React app sends the user to the login page. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() {
        super("not logged in");
    }
}
