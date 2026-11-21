package com.stdiscm.grade.config;

/**
 * The PostgreSQL connection pool is supplied by the shared-db module
 * ({@code com.stdiscm.shareddb.PoolConfig}, auto-configured). Grade-specific
 * connection and pool settings live in {@code application.yml}; no DataSource
 * bean is needed here.
 */
final class DataSourceConfig {
    private DataSourceConfig() { }
}
