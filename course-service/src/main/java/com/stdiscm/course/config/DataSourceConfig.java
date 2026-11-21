package com.stdiscm.course.config;

/**
 * The PostgreSQL connection pool is supplied by the shared-db module
 * ({@code com.stdiscm.shareddb.PoolConfig}, auto-configured). Course-specific
 * connection and pool settings live in {@code application.yml}; no DataSource
 * bean is needed here.
 */
final class DataSourceConfig {
    private DataSourceConfig() { }
}
