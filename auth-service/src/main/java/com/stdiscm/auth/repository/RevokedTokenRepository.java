package com.stdiscm.auth.repository;

import com.stdiscm.auth.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /** Revoked tokens that have expired anyway no longer need tracking. */
    @Modifying
    @Query("delete from RevokedToken r where r.expiresAt < :now")
    int deleteExpired(Instant now);
}
