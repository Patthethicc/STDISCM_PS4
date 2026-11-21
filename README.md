# STDISCM PS4

Distributed systems allow for fault tolerance.  For this exercise you are tasked to create an online enrollment system with different services distributed across multiple nodes.
The system should have the following bare-minimum features:
1) Login/logout. Track sessions across different nodes. (Use OAuth / JWT)
2) View available courses.
3) Students to enroll to open courses.
4) Students to view previous grades.
5) Faculty to be able to upload grades.

The application should be web based using MVC.  The view will be a node on its own.  While the rest of the features / API / controllers will also be on a separate node.
Use of networked virtual machines/bare-metal computers is recommended.
When a node is down, on the features supported by that node should stop working, but the rest of the application should still work.

gRPC implementation is required when communication between nodes are needed.

---

## Design decisions (read before building your part)

**Using shared login**
1. Add the `shared-auth` dependency and set `auth.jwt.public-key-path` (default `deploy/keys/public.pem`).
2. In each method, call `Principal p = RoleGuard.require(Role.STUDENT);` to enforce the role and get the
   caller (`p.userId()`, `p.name()`). Never trust IDs sent in the request.
3. Return errors as gRPC statuses; use a different one for each case (full, closed, already enrolled).

**Setup**
- Java 21, Spring Boot 3.5, Maven, `grpc-spring-boot-starter`, JPA with PostgreSQL.
- One `.proto` per service in `proto/src/main/proto`, like `auth.proto`. (`course.proto` and `grade.proto` are empty.)
- One database per service. Put `schema.sql` and `seed.sql` in `db/<service>/`.
- Settings come from environment variables with defaults in `application.yml` (copy `auth-service`),
  including an explicit connection pool.
- Add your module to `<modules>` in the root `pom.xml`.
- Grades: 0.0 to 4.0 in 0.5 steps.
- Website: Spring Boot server plus React. Token in an HttpOnly cookie, sent as `authorization: Bearer <token>`,
  2-second deadline per call.
- Seed data must use the exact test user IDs under "Test accounts".

**Ports:** auth 9001, course 9002, grade 9003, announcements 9004.

---

## Current Project Status

| Piece | Status |
|---|---|
| Login, logout, shared token checker, announcements, test users | Done and tested |
| Course service, connection pool (Bonus 3) | Done |
| Grade service, replica failover (Bonus 1) | Not started |
| Website, deployment, failure tests, demo | Not started |

**Login:** you send username and password. The login service checks the password against a stored
scrambled version (bcrypt hash, so real passwords are never saved) and returns a 15-minute token.
A wrong password and an unknown username give the **same** message, so nobody can guess which usernames exist.

**Any other action:** your token goes with the request. The service's "doorman" checks the stamp and expiry,
then checks your role for that action. Not logged in gives *unauthenticated*; wrong role gives *permission denied*.

**Announcements:** faculty can post (title up to 200 characters, body up to 5000). The author comes from the
token, so nobody can post as someone else.

---

## Test accounts (password for all: `password123`)

| ID | Username | Role |
|---|---|---|
| 11111111-1111-1111-1111-111111111111 | alice | Student |
| 11111111-1111-1111-1111-111111111112 | bob | Student |
| 11111111-1111-1111-1111-111111111113 | carol | Student |
| 22222222-2222-2222-2222-222222222221 | reyes | Faculty |
| 22222222-2222-2222-2222-222222222222 | santos | Faculty |

Teammates: use these exact IDs in your own seed data.

---

## Run and test what's built

Install: Java 21, Maven, PostgreSQL 15+, openssl (Git Bash includes it on Windows), and for testing
`grpcurl` and `jq`. Use your package manager (Homebrew, winget, scoop, apt, ...).

Use a bash-style terminal: Terminal on macOS/Linux, **Git Bash** on Windows. Run every command from the
`repo/` folder. If a tool isn't found, add it to your PATH and reopen the terminal.

**1. Build** (also runs the 7 automated tests)
```bash
./deploy/gen-keys.sh        # private key (login service only) + public key (everyone), kept out of git
mvn install
```

**2. Databases (one time).** Replace `<ADMIN>` with however you log in to Postgres as admin
(e.g. `postgres` on macOS, `-U postgres -h localhost` on Windows).
```bash
psql <ADMIN> -c "CREATE USER auth PASSWORD 'auth'"       -c "CREATE DATABASE auth_db OWNER auth"
psql <ADMIN> -c "CREATE USER feature PASSWORD 'feature'" -c "CREATE DATABASE feature_db OWNER feature"
psql <ADMIN> -c "CREATE USER course PASSWORD 'course'"   -c "CREATE DATABASE course_db OWNER course"
PGPASSWORD=auth    psql -h localhost -U auth    auth_db    -f db/auth/schema.sql    -f db/auth/seed.sql
PGPASSWORD=feature psql -h localhost -U feature feature_db -f db/feature/schema.sql -f db/feature/seed.sql
PGPASSWORD=course  psql -h localhost -U course  course_db  -f db/course/schema.sql  -f db/course/seed.sql
```

**3. Start the services** (one terminal each, leave running; wait for "gRPC Server started")
```bash
java -jar auth-service/target/auth-service-0.1.0-SNAPSHOT.jar      # port 9001
java -jar feature-service/target/feature-service-0.1.0-SNAPSHOT.jar # port 9004
java -jar course-service/target/course-service-0.1.0-SNAPSHOT.jar   # port 9002
```

**4. Test** (third terminal, from `repo/`). These shortcuts are for zsh/bash; paste them once per terminal:
```bash
G() { grpcurl -plaintext -import-path proto/src/main/proto "$@"; }
login() { G -proto auth.proto -d "{\"username\":\"$1\",\"password\":\"${2:-password123}\"}" localhost:9001 stdiscm.auth.AuthService/Login; }
STUDENT=$(login alice | jq -r .token); FACULTY=$(login reyes | jq -r .token)
ANN=stdiscm.announcement.AnnouncementService
COURSE=stdiscm.course.CourseService
```

| Test | Command | Expected |
|---|---|---|
| Wrong password | `login alice wrong` | Unauthenticated |
| Student login | `login alice` | Token, `role: STUDENT` |
| No token | `G -proto announcement.proto -d '{}' localhost:9004 $ANN/ListAnnouncements` | Unauthenticated |
| Student reads | `G -proto announcement.proto -H "authorization: Bearer $STUDENT" -d '{}' localhost:9004 $ANN/ListAnnouncements` | Welcome announcement (a token from one node works on another) |
| Student posts | `G -proto announcement.proto -H "authorization: Bearer $STUDENT" -d '{"title":"Hi","body":"x"}' localhost:9004 $ANN/PostAnnouncement` | PermissionDenied |
| Faculty posts | same, with `$FACULTY` | New announcement returned |
| Empty title | same as faculty, with `'{"body":"x"}'` | InvalidArgument |
| Logout | `G -proto auth.proto -H "authorization: Bearer $STUDENT" -d '{}' localhost:9001 stdiscm.auth.AuthService/Logout` | `{}`; one row in `revoked_tokens` |

Course service (seed: `CS101` open, `FULL101` full, `CLOSED01` closed, `STDISCM` has alice + carol):

| Test | Command | Expected |
|---|---|---|
| List courses | `G -proto course.proto -H "authorization: Bearer $STUDENT" -d '{}' localhost:9002 $COURSE/ListCourses` | Five seeded courses |
| Enroll | `G -proto course.proto -H "authorization: Bearer $STUDENT" -d '{"course_code":"CS101"}' localhost:9002 $COURSE/Enroll` | Course with `enrolled` +1 |
| Enroll again | same command | AlreadyExists, `ALREADY_ENROLLED` |
| Full | same with `FULL101` | FailedPrecondition, `FULL` |
| Closed | same with `CLOSED01` | FailedPrecondition, `CLOSED` |
| My enrollments | `G -proto course.proto -H "authorization: Bearer $STUDENT" -d '{}' localhost:9002 $COURSE/ListMyEnrollments` | alice's courses |
| Roster as student | `G -proto course.proto -H "authorization: Bearer $STUDENT" -d '{"course_code":"STDISCM"}' localhost:9002 $COURSE/ListRoster` | PermissionDenied |
| Roster as faculty | same with `$FACULTY` | alice and carol |

**Login service down:** press `Ctrl+C` in the auth terminal. Faculty reads and posts still work; `login`
fails with Unavailable. Start it again and login recovers.

**Token expiry:** start auth with `JWT_TTL_MINUTES=1 java -jar auth-service/...`, wait about 70 seconds,
then use the token: Unauthenticated, "invalid or expired token".

**Settings** (environment variables; defaults in each `application.yml`): `GRPC_PORT`,
`AUTH_DB_*` / `FEATURE_DB_*` / `COURSE_DB_*` (HOST, PORT, NAME, USER, PASSWORD),
`DB_POOL_*` (Bonus 3 pool limits and logging), `JWT_PUBLIC_KEY_PATH`,
`JWT_PRIVATE_KEY_PATH`, `JWT_TTL_MINUTES`.

---

**Technology:** Spring Boot 3.5 (Java 21), React, PostgreSQL, gRPC, Docker Compose.
