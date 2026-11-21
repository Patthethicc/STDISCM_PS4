package com.stdiscm.shareddb;

/**
 * Database settings for one service, bound from the {@code db.*} block of its application.yml:
 * <pre>
 * db:
 *   name/user/password
 *   primary:  host, port
 *   replica:  host (empty = no replica), port
 *   pool:     maximum-pool-size, minimum-idle, idle-timeout-ms, connection-timeout-ms,
 *             validation-timeout-ms, max-lifetime-ms
 *   failover: recheck-ms
 *   jdbc-params: extra PgJDBC URL parameters
 * </pre>
 * Timeouts are chosen to fit the gateway's 2-second deadline: one failed primary attempt
 * (connection-timeout-ms) plus a replica attempt must finish well under 2 s.
 */
public class DbSettings {

    private String name;
    private String user;
    private String password;
    /**
     * PgJDBC parameters, in seconds. socketTimeout=1 makes a query on a hung (not crashed) database
     * fail after 1 s instead of waiting past the gateway's 2 s deadline. Our queries take milliseconds.
     */
    private String jdbcParams = "connectTimeout=1&socketTimeout=1&tcpKeepAlive=true";

    private final Endpoint primary = new Endpoint("localhost", 5432);
    private final Endpoint replica = new Endpoint("", 5432);
    private final Pool pool = new Pool();
    private final Failover failover = new Failover();

    public static class Endpoint {
        private String host;
        private int port;

        public Endpoint() { this("", 5432); }
        Endpoint(String host, int port) { this.host = host; this.port = port; }

        public boolean isConfigured() { return host != null && !host.isBlank(); }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public static class Pool {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long idleTimeoutMs = 60_000;
        private long connectionTimeoutMs = 500;     // Hikari minimum is 250
        private long validationTimeoutMs = 300;     // must be below connectionTimeoutMs, minimum 250
        private long maxLifetimeMs = 600_000;

        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int v) { this.maximumPoolSize = v; }
        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int v) { this.minimumIdle = v; }
        public long getIdleTimeoutMs() { return idleTimeoutMs; }
        public void setIdleTimeoutMs(long v) { this.idleTimeoutMs = v; }
        public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
        public void setConnectionTimeoutMs(long v) { this.connectionTimeoutMs = v; }
        public long getValidationTimeoutMs() { return validationTimeoutMs; }
        public void setValidationTimeoutMs(long v) { this.validationTimeoutMs = v; }
        public long getMaxLifetimeMs() { return maxLifetimeMs; }
        public void setMaxLifetimeMs(long v) { this.maxLifetimeMs = v; }
    }

    public static class Failover {
        /** After the primary fails, skip it for this long before trying it again. */
        private long recheckMs = 5_000;

        public long getRecheckMs() { return recheckMs; }
        public void setRecheckMs(long v) { this.recheckMs = v; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getJdbcParams() { return jdbcParams; }
    public void setJdbcParams(String jdbcParams) { this.jdbcParams = jdbcParams; }
    public Endpoint getPrimary() { return primary; }
    public Endpoint getReplica() { return replica; }
    public Pool getPool() { return pool; }
    public Failover getFailover() { return failover; }
}
