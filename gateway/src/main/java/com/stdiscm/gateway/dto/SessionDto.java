package com.stdiscm.gateway.dto;

import com.stdiscm.sharedauth.AuthContext.Principal;

/** What the React app knows about the logged-in user (read from the token). */
public record SessionDto(String userId, String role, String name, long expiresAt) {
    public static SessionDto from(Principal p) {
        return new SessionDto(p.userId(), p.role().name(), p.name(), p.expiresAt().getEpochSecond());
    }
}
