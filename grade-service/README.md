# Grade service (port 9003)

Students view their own previous grades; faculty upload grades for the courses they teach.
The grade service calls no other service: course code and title are stored with every grade, and
`faculty_courses` says who teaches what. With the course node down, grades and uploads still work.

## API (contract: `proto/src/main/proto/grade.proto`, package `stdiscm.grade`)

| RPC | Who | What | Gateway route |
|---|---|---|---|
| `GetMyGrades{}` | STUDENT | The caller's grades, newest term first. Student ID from the token. | `GET /api/grades` |
| `ListMyTeachingCourses{}` | FACULTY | Courses the caller teaches (code, title). Fills the upload page. | `GET /api/grades/teaching` |
| `UploadGrades{course_code, term, grades[]}` | FACULTY, own course | One all-or-nothing batch (max 500). New grades inserted, existing ones (same student, course, term) updated. Returns `saved`. | `POST /api/grades` |

Grades are 0.0 to 4.0 in 0.5 steps. Terms are free text as typed on the upload page (e.g. `T1 2026-2027`,
at most 20 characters, stored upper-case); known formats sort newest first.

## Errors (and what the gateway shows)

| Status | When | HTTP |
|---|---|---|
| UNAUTHENTICATED | no token, bad signature, expired (shared-auth) | 401 |
| PERMISSION_DENIED | wrong role, or faculty uploading for a course they don't teach ("you do not teach X") | 403 |
| INVALID_ARGUMENT | empty course code or term, empty or oversized batch, bad rows: the message lists every bad row, e.g. `1 problem, nothing saved: row 1: grade 3.7 is not a multiple of 0.5` | 400 |
| UNAVAILABLE | database unreachable, or upload while the primary DB is down (replica is read-only) | 503 |

## Code map

```
grpc/GradeGrpcService      role check, protobuf <-> values, DbRetry for reads
grpc/GradeExceptionAdvice  InvalidRequestException -> INVALID_ARGUMENT, NotTeachingException -> PERMISSION_DENIED
service/GradeService       rules + transactions (upload = one transaction)
service/GradeRules         input checks and term ordering, no Spring/gRPC (unit-tested)
repository/GradeRepository, FacultyCourseRepository   JPA reads
repository/GradeBatchWriter  JDBC batches of INSERT ... ON CONFLICT DO UPDATE
(no DataSource code)       shared-db builds the pool (Bonus 3); with db.failover.replica-host set it adds the replica (Bonus 1)
```

## Run locally

```bash
psql <ADMIN> -c "CREATE USER grade PASSWORD 'grade'" -c "CREATE DATABASE grade_db OWNER grade"
PGPASSWORD=grade psql -h localhost -U grade grade_db -f db/grade/schema.sql -f db/grade/seed.sql
mvn install
java -jar grade-service/target/grade-service-0.1.0-SNAPSHOT.jar          # port 9003
```

With failover, using the one-machine replica demo instead (`deploy/replication/local-demo.sh up`):

```bash
GRADE_DB_PORT=55432 GRADE_DB_REPLICA_HOST=localhost GRADE_DB_REPLICA_PORT=55433 \
  java -jar grade-service/target/grade-service-0.1.0-SNAPSHOT.jar
```

## Test with grpcurl (after the shortcuts in the root README: `G`, `login`, `$STUDENT`, `$FACULTY`)

```bash
GR=stdiscm.grade.GradeService
g() { G -proto grade.proto -H "authorization: Bearer $1" -d "$2" localhost:9003 $GR/$3; }
```

| Test | Command | Expected |
|---|---|---|
| Student's grades | `g $STUDENT '{}' GetMyGrades` | alice: CSARCH2 4.0 (T3 2025-2026), CCPROG3 3.5 (T2 2025-2026) |
| Faculty calls GetMyGrades | `g $FACULTY '{}' GetMyGrades` | PermissionDenied |
| Faculty's courses | `g $FACULTY '{}' ListMyTeachingCourses` | reyes: CS101, STDISCM |
| Upload | `g $FACULTY '{"course_code":"STDISCM","term":"T1 2026-2027","grades":[{"student_id":"11111111-1111-1111-1111-111111111111","grade":3.5}]}' UploadGrades` | `saved: 1` |
| Same again, grade 3.0 | same request | `saved: 1`; alice now sees 3.0 (updated, not duplicated) |
| Student uploads | same request with `$STUDENT` | PermissionDenied |
| Not their course | reyes uploads `FULL101` or `NOT-MY-COURSE` | PermissionDenied "you do not teach ..." |
| Bad grade | `"grade":3.7` | InvalidArgument naming the row; nothing saved |
| Primary DB down | `local-demo.sh stop-primary`, then GetMyGrades / UploadGrades | grades still shown / Unavailable |

The browser flow and node failures are covered by `tests/e2e/FailureTests.java` (see `DEMO.md`).

## For teammates

- **Course service (Pat):** `db/grade/seed.sql` uses your course codes and titles (STDISCM, CS101 for reyes;
  FULL101, CLOSED01 for santos), so each class roster and its grade upload page agree. Bonus 1 needed no code in
  your service: `application.yml` has a `db.failover` block, and compose passes `COURSE_DB_REPLICA_HOST/PORT`.
  Your `db.pool` settings (Bonus 3) apply to both the primary and the replica pool. Only while a replica is configured,
  the connection timeout becomes 500 ms (`db.failover.connection-timeout-ms`) so a failover fits in the gateway's
  2-second deadline; without a replica your 3 s pool is used unchanged.
- **Auth + announcements (Meg):** same `db.failover` block in each `application.yml` (`AUTH_DB_*` / `FEATURE_DB_*`
  variables, plus `*_DB_REPLICA_HOST/PORT`). While a primary DB is down, login and reading announcements work;
  logout and posting return UNAVAILABLE.
