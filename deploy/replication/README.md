# Bonus 1: redundant databases (PostgreSQL streaming replication + failover)

Every service database has a **primary** (read-write, on the service's VM) and a **hot standby
replica** (read-only, on the replica VM) that streams the primary's WAL continuously. Each service's
DataSource (`shared-db/ReplicaFailoverDataSource`, switched on by `db.failover.replica-host`) uses the primary, and when the primary can't be
reached, it serves from the replica:

| Primary database | Reads (grades, courses, login, announcements) | Writes (upload, enroll, logout, post) |
|---|---|---|
| up | primary | primary |
| down | **replica** (measured: 18 ms after a crash, ~1 s if the DB host hangs) | UNAVAILABLE, shown as "temporarily unavailable" |
| back | primary again within 5 s (automatic failback) | primary |

Replication is **asynchronous**: the primary never waits for the replica, so a dead replica VM never
blocks the system. The price: a write committed in the last moment before a crash may not have reached
the replica yet. Primaries and replicas use the same image (`postgres:16-alpine`): streaming replication
needs the same PostgreSQL major version on both sides.

## Files

| File | Where it runs |
|---|---|
| `primary-init.sh` | each primary DB container, once at first start (mounted as `00-replication.sh`): replication login, slot, pg_hba rule |
| `replica-entrypoint.sh` | each replica container: `pg_basebackup -R` on first start, then runs as hot standby |
| `../docker-compose.replica.yml` | add-on to Jomi's `docker-compose.yml` (which stays untouched): the `replica` profile (four standbys), the replica settings of every service, replication port on each primary |
| `local-demo.sh` | primary + replica on one machine without Docker, for practice |

## On the VMs

Run from `deploy/`. Add the override file to every command, on every VM, so the services know their replica:

```bash
C="docker compose --env-file .env -f docker-compose.yml -f docker-compose.replica.yml"
$C --profile grade up -d --build      # a node VM (auth / course / grade / feature): service + primary DB
$C --profile replica up -d            # the replica VM: the four standbys
$C --profile all up -d --build        # one machine, replicas included
```

Order: node VMs first (the primary must exist), then the replica VM. On the replica VM set
`<NAME>_DB_PRIMARY_HOST` to each node VM's IP and `<NAME>_DB_PRIMARY_PORT` to its published port (table below).
On each node VM set `<NAME>_REPLICA_HOST` to the replica VM's IP and `<NAME>_REPLICA_PORT` to 5441-5444.
Optional in `.env` (defaults in brackets): `REPLICATION_PASSWORD` [replpass], plus the host/port variables.
Without the override file the system runs exactly as before, with no replicas.

| Database | Primary (service VM) | Replica (replica VM) | Slot | Service settings |
|---|---|---|---|---|
| auth_db | auth-db, port 5401 | auth-db-replica, 5441 | auth_replica | `AUTH_REPLICA_HOST/PORT` |
| course_db | course-db, 5402 | course-db-replica, 5442 | course_replica | `COURSE_REPLICA_HOST/PORT` |
| grade_db | grade-db, 5403 | grade-db-replica, 5443 | grade_replica | `GRADE_REPLICA_HOST/PORT` |
| feature_db | feature-db, 5404 | feature-db-replica, 5444 | feature_replica | `FEATURE_REPLICA_HOST/PORT` |

`--profile all` (one machine) starts the replicas too; the containers find each other by name.

The primary's init scripts run only on an **empty** volume. A DB that already ran before Bonus 1 needs
one reset (`$C --profile <name> down -v`) so the replication login and slot are created.

## Check it

```sql
-- on the primary: one row per replica, state = streaming
SELECT client_addr, state, replay_lag FROM pg_stat_replication;
-- on the replica: t
SELECT pg_is_in_recovery();
-- write on the replica: ERROR 25006 cannot execute INSERT in a read-only transaction (expected)
```

## Demo script

See `DEMO.md`, "Bonus 1". Stop **only the database** (`$C --profile grade stop grade-db`),
not the whole grade node: the grade service keeps running on the replica.

## Promotion (optional, manual)

To accept writes while the primary is gone for good, promote the replica: `SELECT pg_promote();` on it.
Then **do not start the old primary** as it is: two primaries that both accept writes (split brain) would
drift apart, and the service would go back to the old one. Rebuild the old primary as a new replica of the
promoted one and swap the host settings. This is why failover here is read-only by default.

## Notes

- A replication slot keeps WAL on the primary until its replica has it. If a replica is retired for good,
  drop its slot (`SELECT pg_drop_replication_slot('grade_replica');`) or the primary's disk fills up.
- Physical replication copies a whole PostgreSQL instance. Each service has its own instance, so each needs
  its own replica (four standby containers on the replica VM).
- Every service (auth, course, grade, announcements) gets failover from `shared-db` through its `db.failover` block in `application.yml`; the pool limits are Pat's `db.pool` settings (Bonus 3).
