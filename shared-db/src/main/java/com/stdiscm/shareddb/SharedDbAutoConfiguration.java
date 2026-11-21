package com.stdiscm.shareddb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Adding the shared-db dependency gives every service:
 * <ul>
 *   <li>{@link DatabaseExceptionAdvice}: database outages reach the caller as UNAVAILABLE, not UNKNOWN;</li>
 *   <li>Bonus 1: when {@code db.failover.replica-host} is set, the DataSource is a
 *       {@link ReplicaFailoverDataSource} (primary pool + read-only replica pool). This runs before
 *       {@link PoolConfig}, which then sees a DataSource and stands back. With no replica host,
 *       nothing changes and {@link PoolConfig} (Bonus 3) builds the single pool as before.</li>
 * </ul>
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class, beforeName = "com.stdiscm.shareddb.PoolConfig")
@EnableConfigurationProperties({PoolProperties.class, FailoverProperties.class})
public class SharedDbAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DatabaseExceptionAdvice databaseExceptionAdvice() {
        return new DatabaseExceptionAdvice();
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(ReplicaConfigured.class)
    @ConditionalOnProperty(prefix = "spring.datasource", name = "url")
    static class Failover {

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(DataSource.class)
        ReplicaFailoverDataSource dataSource(
                @Value("${spring.datasource.url}") String url,
                @Value("${spring.datasource.username:}") String username,
                @Value("${spring.datasource.password:}") String password,
                @Value("${spring.datasource.driver-class-name:}") String driverClassName,
                PoolProperties pool, FailoverProperties failover) {
            return FailoverDataSourceFactory.create(url, username, password, driverClassName, pool, failover);
        }

        /** Keeps Bonus 3's "connections in use" log line for the primary pool (PoolConfig's monitor needs a plain pool). */
        @Bean
        @ConditionalOnMissingBean
        PoolMonitor poolMonitor(ReplicaFailoverDataSource dataSource, PoolProperties pool) {
            return new PoolMonitor(dataSource.primary(), pool.getLogIntervalMs());
        }
    }
}
