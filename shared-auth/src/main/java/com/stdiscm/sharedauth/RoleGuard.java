package com.stdiscm.sharedauth;

import com.stdiscm.sharedauth.AuthContext.Principal;
import com.stdiscm.sharedauth.AuthContext.Role;
import io.grpc.Status;

import java.util.Arrays;

/** Role checks for service methods: {@code Principal p = RoleGuard.require(Role.FACULTY);} */
public final class RoleGuard {

    private RoleGuard() { }

    /**
     * Returns the caller if they hold one of {@code allowed}.
     * UNAUTHENTICATED when there is no valid token, PERMISSION_DENIED when the role is wrong.
     */
    public static Principal require(Role... allowed) {
        Principal p = AuthContext.require();
        if (!Arrays.asList(allowed).contains(p.role())) {
            throw Status.PERMISSION_DENIED
                    .withDescription("role " + p.role() + " is not allowed to call this method")
                    .asRuntimeException();
        }
        return p;
    }
}
