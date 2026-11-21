package com.stdiscm.feature.config;

import com.stdiscm.shareddb.DbSettings;
import com.stdiscm.shareddb.FailoverDataSourceFactory;
import com.stdiscm.shareddb.ReplicaFailoverDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bonus 1, extended to the feature-service: explicit Hikari pools for the primary and the replica,
 * falling back to the read-only replica while the primary is down (see shared-db).
 * Settings: the db.* block in application.yml. Replaces spring.datasource auto-configuration.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "db")
    public DbSettings dbSettings() {
        return new DbSettings();
    }

    @Bean(destroyMethod = "close")
    public ReplicaFailoverDataSource dataSource(DbSettings db) {
        return FailoverDataSourceFactory.create("feature-service", db);
    }
}
