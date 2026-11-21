package com.stdiscm.shareddb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Builds the one HikariCP pool every service uses instead of relying on the
 * starter's implicit defaults.
 *
 * <p>This runs <em>before</em> Spring Boot's {@link DataSourceAutoConfiguration}, so
 * the service gets this explicitly configured pool. It activates whenever
 * {@code spring.datasource.url} is set, which every service already provides.
 *
 * <p>Add this module's dependency and set the usual {@code spring.datasource.*}
 * values plus the {@code db.pool.*} block; no Java code is needed in the service.
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass({HikariDataSource.class, DataSource.class})
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@EnableConfigurationProperties(PoolProperties.class)
public class PoolConfig {

    private static final Logger log = LoggerFactory.getLogger(PoolConfig.class);

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public HikariDataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.driver-class-name:}") String driverClassName,
            PoolProperties pool) {

        HikariConfig config = buildConfig(url, username, password, driverClassName, pool);
        HikariDataSource dataSource = new HikariDataSource(config);

        log.info("DB pool '{}' configured: maximumPoolSize={}, minimumIdle={}, "
                        + "idleTimeout={}ms, connectionTimeout={}ms, maxLifetime={}ms",
                pool.getName(), pool.getMaximumPoolSize(), pool.getMinimumIdle(),
                pool.getIdleTimeoutMs(), pool.getConnectionTimeoutMs(), pool.getMaxLifetimeMs());
        return dataSource;
    }

    /** Package-private so the configuration can be tested without a live database. */
    HikariConfig buildConfig(String url, String username, String password,
                             String driverClassName, PoolProperties pool) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        if (!username.isEmpty()) config.setUsername(username);
        if (!password.isEmpty()) config.setPassword(password);
        if (!driverClassName.isEmpty()) config.setDriverClassName(driverClassName);

        config.setPoolName(pool.getName());
        config.setMaximumPoolSize(pool.getMaximumPoolSize());
        config.setMinimumIdle(pool.getMinimumIdle());
        config.setIdleTimeout(pool.getIdleTimeoutMs());
        config.setConnectionTimeout(pool.getConnectionTimeoutMs());
        config.setMaxLifetime(pool.getMaxLifetimeMs());
        if (pool.getLeakDetectionThresholdMs() > 0) {
            config.setLeakDetectionThreshold(pool.getLeakDetectionThresholdMs());
        }
        config.setMetricsTrackerFactory(new PoolMetricsLogger(pool.isLogCheckouts()));
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public PoolMonitor poolMonitor(DataSource dataSource, PoolProperties pool) {
        return new PoolMonitor(dataSource, pool.getLogIntervalMs());
    }
}
