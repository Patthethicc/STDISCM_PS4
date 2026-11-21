package com.stdiscm.grade.config;

import com.stdiscm.shareddb.DbSettings;
import com.stdiscm.shareddb.FailoverDataSourceFactory;
import com.stdiscm.shareddb.ReplicaFailoverDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The grade service's only DataSource: explicit Hikari pools for the primary and the replica,
 * wrapped so connections come from the replica while the primary is down (Bonus 1).
 * Because this bean exists, Spring Boot skips its own spring.datasource auto-configuration,
 * and JPA, transactions and JdbcTemplate all use this one.
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
        return FailoverDataSourceFactory.create("grade-service", db);
    }
}
