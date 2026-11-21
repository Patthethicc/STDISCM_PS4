package com.stdiscm.shareddb;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pool must take its limits and timeouts from db.pool, not from implicit defaults. */
class PoolConfigTest {

    @Test
    void appliesExplicitLimitsAndTimeouts() {
        PoolProperties pool = new PoolProperties();
        pool.setName("test-pool");
        pool.setMaximumPoolSize(7);
        pool.setMinimumIdle(1);
        pool.setIdleTimeoutMs(20_000);
        pool.setConnectionTimeoutMs(1_500);
        pool.setMaxLifetimeMs(60_000);

        HikariConfig config = new PoolConfig().buildConfig(
                "jdbc:postgresql://localhost:5432/test", "user", "pass", "", pool);

        assertEquals("test-pool", config.getPoolName());
        assertEquals(7, config.getMaximumPoolSize());
        assertEquals(1, config.getMinimumIdle());
        assertEquals(20_000, config.getIdleTimeout());
        assertEquals(1_500, config.getConnectionTimeout());
        assertEquals(60_000, config.getMaxLifetime());
    }
}
