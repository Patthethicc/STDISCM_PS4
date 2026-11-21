# Demo Script (P4: Distributed Enrollment System)

Target length: ~10 minutes. Record the browser and a terminal side by side.
`VIEW` below means the view VM's address, e.g. `http://192.168.56.10`.

## Before recording

- [ ] All five nodes up (see `deploy/SETUP.md`), site opens at `VIEW`
- [ ] Fresh data: on each service VM, `docker compose --profile <node> down -v && docker compose --profile <node> up -d`
- [ ] Course seed has a course with exactly 5 free slots for the concurrency test (e.g. `STRESS01`, capacity 5)
- [ ] Terminal open in the repo folder with Java 21 (for the test scripts) and `grpcurl` (for the view-down scene)
- [ ] One terminal per service VM ready to run `stop` / `start`

## 0. Architecture (30 s)

Show the node table from `deploy/SETUP.md`.

> "Each feature runs on its own node with its own database. The view node is the website: React
> in the browser and a Spring gateway as the controller. It talks to every other node only over
> gRPC, with a 2-second deadline on each call. Each node checks the JWT itself with the public key,
> so no node depends on the auth node to verify a request."

## 1. Normal flow (2 min)

In the browser:

1. Log in as **alice** (`password123`). Point out the student nav: Courses, My Enrollments, My Grades.
2. **Courses**: enroll in an open course. Try a **full** course (FULL), a **closed** one (CLOSED), and the
   same course twice (ALREADY_ENROLLED).
3. **My Enrollments** shows the new course. **My Grades** shows past grades.
4. Log out. Log in as **reyes** (faculty nav: Courses, Upload Grades).
5. **Upload Grades**: the roster comes from the course node. Give alice a grade, upload.
6. Log out, log in as alice, **My Grades** shows it.

> "One token, issued once by the auth node, was accepted by the course, grade and feature nodes."

Then run the automated version (it saves sessions for the next scenes):

```bash
java tests/e2e/FailureTests.java functional VIEW
```

Covers: login, enroll errors, faculty upload → student sees it, student calling UploadGrades
(PERMISSION_DENIED), faculty uploading for a course they don't teach (PERMISSION_DENIED), logout.

## 2. Logout and the old cookie (30 s)

Shown by the `functional` run ("reusing the old cookie after logout -> 401").

> "Logout clears the cookie and adds the token ID to the auth node's revocation list. The other
> nodes only check signature and expiry, so a stolen token still works against them for at most
> 15 minutes. That's the trade-off for not calling the auth node on every request."

## 3. Concurrency (1 min)

```bash
java tests/e2e/ConcurrentEnrollTest.java STRESS01 VIEW
```

Expected: 20 requests, **exactly 5 OK**, 15 FULL, course ends at 5/5.

> "The course service enrolls with one atomic update guarded by the open and capacity checks,
> plus a unique constraint, so the database prevents overbooking. There's no check-then-write."

## 4. Node failures (4 min)

For each one: stop the node, show the browser, run the script, restart, show recovery.

### Auth node down
On the auth VM: `docker compose --profile auth stop`
- Browser (still logged in as alice): Courses and My Grades still work.
- Open a private window, try to log in: "Login is temporarily unavailable."
- `java tests/e2e/FailureTests.java auth-down VIEW`

> "New logins fail, but everyone already logged in keeps working, because every node verifies tokens itself."

Restart: `docker compose --profile auth start`

### Course node down
On the course VM: `docker compose --profile course stop`
- Courses / My Enrollments: yellow "Course service is temporarily unavailable." Nav still works.
- My Grades still works.
- As reyes, **Upload Grades**: roster unavailable, but type a student ID
  (`11111111-1111-1111-1111-111111111112` for bob) and upload. It saves.
- `java tests/e2e/FailureTests.java course-down VIEW`

Restart: `docker compose --profile course start`

### Grade node down
On the grade VM: `docker compose --profile grade stop`
- My Grades and Upload Grades: unavailable. Courses and enrolling still work.
- `java tests/e2e/FailureTests.java grade-down VIEW`

Restart: `docker compose --profile grade start`

### View node down
On the view VM: `docker compose --profile view stop`. The website is gone.
The services still answer direct gRPC calls with a token (run from the repo folder):

```bash
G() { grpcurl -plaintext -import-path proto/src/main/proto "$@"; }
TOKEN=$(G -proto auth.proto -d '{"username":"alice","password":"password123"}' AUTH_IP:9001 stdiscm.auth.AuthService/Login | jq -r .token)
G -proto course.proto -H "authorization: Bearer $TOKEN" -d '{}' COURSE_IP:9002 stdiscm.course.CourseService/ListCourses
G -proto grade.proto  -H "authorization: Bearer $TOKEN" -d '{}' GRADE_IP:9003  stdiscm.grade.GradeService/GetMyGrades
```

Restart: `docker compose --profile view start`

### Recovery
After restarting a node, refresh the browser: its pages work again within a few seconds,
and no other node was restarted.

```bash
java tests/e2e/FailureTests.java recovered VIEW
```

> "The gateway resets its reconnect backoff when a call fails, so a restarted node is picked up
> on the next request instead of after gRPC's default wait of up to two minutes."

## 5. Bonuses (1–2 min)

- **Bonus 2, announcements on their own node:** post as reyes, read as alice; stop the feature
  node (`--profile feature stop`) and only Announcements goes unavailable.
- **Bonus 1, redundant database (grade node):** stop the grade primary DB; grades still load from the replica.
  (Neo: fill in the exact commands once the replica is set up.)
- **Bonus 3, connection pooling (course node):** run the load test while watching the
  course node's pool lines. In the course node's terminal:
  `docker compose --profile course logs -f course-service | grep 'pool\['`.
  Each line shows `inUse`, `idle`, `total`, `waiting` and `max`; `inUse` never exceeds
  the limit (10) and `waiting` shows requests queueing when the pool is saturated.

## Closing line

> "Each node owns one feature and its data, talks only over gRPC, and verifies tokens on its own.
> When a node goes down, only its features stop; when it comes back, they recover without
> restarting anything else."
