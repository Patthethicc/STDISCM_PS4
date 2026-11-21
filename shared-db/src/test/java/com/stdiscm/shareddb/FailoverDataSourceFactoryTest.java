package com.stdiscm.shareddb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailoverDataSourceFactoryTest {

    @Test
    void replicaUrlKeepsDatabaseAndSwapsHostAndPort() {
        assertEquals("jdbc:postgresql://replica:5443/grade_db",
                FailoverDataSourceFactory.swapHost("jdbc:postgresql://grade-db:5432/grade_db", "replica", 5443));
    }

    @Test
    void paramsAreAppendedWithQuestionMarkOrAmpersand() {
        assertEquals("jdbc:postgresql://h:5432/d?connectTimeout=1",
                FailoverDataSourceFactory.withParams("jdbc:postgresql://h:5432/d", "connectTimeout=1"));
        assertEquals("jdbc:postgresql://h:5432/d?ssl=false&connectTimeout=1",
                FailoverDataSourceFactory.withParams("jdbc:postgresql://h:5432/d?ssl=false", "connectTimeout=1"));
        assertEquals("jdbc:postgresql://h:5432/d", FailoverDataSourceFactory.withParams("jdbc:postgresql://h:5432/d", " "));
    }

    @Test
    void failoverIsOffWithoutReplicaHostAndOnWithOne() {
        FailoverProperties f = new FailoverProperties();
        assertFalse(f.isEnabled());
        f.setReplicaHost("  ");
        assertFalse(f.isEnabled());
        f.setReplicaHost("grade-db-replica");
        assertTrue(f.isEnabled());
    }

    @Test
    void bothPoolsUseTheSharedPoolSettingsButTheShortFailoverTimeouts() throws Exception {
        PoolProperties pool = new PoolProperties();
        pool.setName("grade-pool");
        pool.setMaximumPoolSize(7);          // Bonus 3's limit must carry over to both pools
        FailoverProperties f = new FailoverProperties();
        f.setReplicaHost("replica");
        f.setReplicaPort(5443);

        // Port 1 is closed, so the pools stay empty; initializationFailTimeout=-1 lets them start anyway.
        try (ReplicaFailoverDataSource ds = FailoverDataSourceFactory.create(
                "jdbc:postgresql://127.0.0.1:1/grade_db", "u", "p", "", pool, f)) {
            HikariDataSource primary = (HikariDataSource) ds.primary();
            HikariDataSource replica = (HikariDataSource) ds.replica();
            assertEquals("grade-pool-primary", primary.getPoolName());
            assertEquals("grade-pool-replica", replica.getPoolName());
            assertEquals(7, primary.getMaximumPoolSize());
            assertEquals(7, replica.getMaximumPoolSize());
            assertEquals(500, primary.getConnectionTimeout());
            assertEquals(300, primary.getValidationTimeout());
            assertFalse(primary.isReadOnly());
            assertTrue(replica.isReadOnly());
            assertTrue(primary.getJdbcUrl().endsWith("/grade_db?connectTimeout=1&socketTimeout=1&tcpKeepAlive=true"));
            assertTrue(replica.getJdbcUrl().startsWith("jdbc:postgresql://replica:5443/grade_db?"));
        }
    }

    @Test
    void noReplicaPoolWhenNoReplicaHost() throws Exception {
        PoolProperties pool = new PoolProperties();
        try (ReplicaFailoverDataSource ds = FailoverDataSourceFactory.create(
                "jdbc:postgresql://127.0.0.1:1/d", "u", "p", "", pool, new FailoverProperties())) {
            assertNull(ds.replica());
        }
    }
}
