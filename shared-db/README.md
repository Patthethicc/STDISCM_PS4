# shared-db (Bonus 3)

One explicit PostgreSQL connection pool for every service. Adding the dependency
is enough: `PoolConfig` is an auto-configuration, so it replaces Spring Boot's
default pool before `DataSourceAutoConfiguration` runs.

## Use it in a service

Add the dependency to the service `pom.xml` (the root pom manages the version):

```xml
<dependency>
  <groupId>com.stdiscm</groupId>
  <artifactId>shared-db</artifactId>
</dependency>
```

Keep the normal connection settings and add the pool block:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${COURSE_DB_HOST:localhost}:5432/course_db
    username: ${COURSE_DB_USER:course}
    password: ${COURSE_DB_PASSWORD:course}

db:
  pool:
    name: course-pool
    maximum-pool-size: 10       # hard limit
    minimum-idle: 2
    idle-timeout-ms: 60000      # close idle connections after 1 minute
    connection-timeout-ms: 3000 # fail a request after 3 seconds instead of hanging
    max-lifetime-ms: 1800000
    log-interval-ms: 10000      # log in-use/idle/total every 10 seconds
    log-checkouts: false        # true = one line per borrow (load-test demo)
```

No Java code is required in the service. `auth-service`, `feature-service`,
`course-service` and `grade-service` already include this module and the same
`db.pool` block. `grade-service` has no feature code yet, but its `pom.xml` and
`application.yml` are wired; Member 3 only needs to add the application class and
list the module in the root `pom.xml`.

## What gets logged

Startup:

```
DB pool 'course-pool' configured: maximumPoolSize=10, minimumIdle=2, idleTimeout=60000ms, connectionTimeout=3000ms, maxLifetime=1800000ms
```

Every `log-interval-ms`:

```
pool[course-pool] inUse=3, idle=2, total=5, waiting=0, max=10
```

With `log-checkouts: true`, each borrow also logs:

```
pool[course-pool] connection checked out: inUse=5, idle=0, total=5, waiting=2, max=10
```

A timeout waiting for a connection logs a WARN with the same numbers.

## Quick check for the demo

```bash
# course node terminal
docker compose --profile course logs -f course-service | grep 'pool\['
```

Run `tests/e2e/ConcurrentEnrollTest.java STRESS01` while watching the log. `inUse`
must never exceed `max` (10), and `waiting` shows requests queueing when the pool
is saturated.
