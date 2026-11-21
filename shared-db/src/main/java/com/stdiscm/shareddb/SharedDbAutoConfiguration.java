package com.stdiscm.shareddb;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Adding the shared-db dependency registers {@link DatabaseExceptionAdvice}, so database
 * outages reach the caller as UNAVAILABLE instead of UNKNOWN. The DataSource itself is built
 * in each service's DataSourceConfig with {@link FailoverDataSourceFactory}.
 */
@AutoConfiguration
public class SharedDbAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DatabaseExceptionAdvice databaseExceptionAdvice() {
        return new DatabaseExceptionAdvice();
    }
}
