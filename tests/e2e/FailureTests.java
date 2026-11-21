import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * End-to-end and failure tests, run against the website (view node) like a browser would.
 * No build needed (Java 21 runs a single source file):
 *
 *   java tests/e2e/FailureTests.java <mode> [http://VIEW_IP]
 *
 * Modes, in demo order:
 *   functional   Everything up. Logs in, enrolls, uploads grades, checks permission and enrollment
 *                errors and logout. Also saves alice's and reyes's sessions for the modes below.
 *   login        Only refreshes the saved sessions (tokens last 15 minutes).
 *   auth-down    Run after stopping the auth node: new logins fail, saved sessions keep working.
 *   course-down  Run after stopping the course node: course pages unavailable, grades + manual upload work.
 *   grade-down   Run after stopping the grade node: grade pages unavailable, courses + enrollment work.
 *   recovered    Run after restarting the stopped node: everything works again.
 *
 * Exit code 0 = all checks passed.
 */
public class FailureTests {

    static final String PASSWORD = "password123";
    static final String ALICE_ID = "11111111-1111-1111-1111-111111111111";
    static final String BOB_ID = "11111111-1111-1111-1111-111111111112";
    static final Path SESSIONS = Path.of("tests", "e2e", ".sessions");

    static String base;
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static int passed = 0, failed = 0, skipped = 0;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: java tests/e2e/FailureTests.java <functional|login|auth-down|course-down|grade-down|recovered> [http://VIEW_IP]");
            System.exit(2);
        }
        String mode = args[0];
        base = (args.length > 1 ? args[1] : "http://localhost").replaceAll("/+$", "");
        System.out.println("== " + mode + " against " + base + "\n");

        switch (mode) {
            case "functional" -> functional();
            case "login" -> saveSessions();
            case "auth-down" -> authDown();
            case "course-down" -> courseDown();
            case "grade-down" -> gradeDown();
            case "recovered" -> recovered();
            default -> { System.out.println("unknown mode: " + mode); System.exit(2); }
        }

        System.out.printf("%n%d passed, %d failed, %d skipped%n", passed, failed, skipped);
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ scenarios

    static void functional() throws Exception {
        section("Login");
        Res bad = post("/api/auth/login", json("username", "alice", "password", "wrong"), null);
        check("wrong password is rejected (401)", bad.status == 401, bad);
        String alice = login("alice");
        String bob = login("bob");
        String reyes = login("reyes");
        Res me = get("/api/auth/me", alice);
        check("session reads role from the token", me.status == 200 && me.body.contains("\"STUDENT\""), me);

        section("Student: courses, enroll, grades (one token accepted by every node)");
        Res courses = get("/api/courses", alice);
        check("view courses", courses.status == 200, courses);
        Res grades = get("/api/grades", alice);
        check("view grades", grades.status == 200, grades);
        Res mine = get("/api/enrollments", alice);
        check("view my enrollments", mine.status == 200, mine);

        List<Course> list = Course.parseAll(courses.body);
        Optional<Course> open = list.stream().filter(c -> c.open && c.enrolled < c.capacity).findFirst();
        if (open.isPresent()) {
            String code = open.get().code;
            Res first = post("/api/courses/" + code + "/enroll", "{}", bob);
            check("enroll in open course " + code, first.status == 200 || first.body.contains("ALREADY_ENROLLED"), first);
            Res second = post("/api/courses/" + code + "/enroll", "{}", bob);
            check("enroll twice -> ALREADY_ENROLLED", second.status == 409 && second.body.contains("ALREADY_ENROLLED"), second);
        } else {
            skip("no open course with free slots in the seed data");
        }

        Optional<Course> full = list.stream().filter(c -> c.open && c.enrolled >= c.capacity).findFirst();
        if (full.isPresent()) {
            Res r = post("/api/courses/" + full.get().code + "/enroll", "{}", alice);
            check("enroll in full course " + full.get().code + " -> FULL", r.status == 409 && r.body.contains("FULL"), r);
        } else {
            skip("no full course in the seed data (enroll -> FULL)");
        }
        Optional<Course> closed = list.stream().filter(c -> !c.open).findFirst();
        if (closed.isPresent()) {
            Res r = post("/api/courses/" + closed.get().code + "/enroll", "{}", alice);
            check("enroll in closed course " + closed.get().code + " -> CLOSED", r.status == 409 && r.body.contains("CLOSED"), r);
        } else {
            skip("no closed course in the seed data (enroll -> CLOSED)");
        }

        section("Faculty uploads grades, then the student sees them");
        Res teaching = get("/api/grades/teaching", reyes);
        check("faculty's courses", teaching.status == 200, teaching);
        String teachCode = field(teaching.body, "courseCode");
        if (teachCode != null) {
            Res up = post("/api/grades", gradeBody(teachCode, "E2E-TEST", ALICE_ID, 3.5), reyes);
            check("faculty uploads a grade for alice in " + teachCode, up.status == 200, up);
            Res after = get("/api/grades", alice);
            check("alice sees the new grade", after.status == 200 && after.body.contains("E2E-TEST") && after.body.contains(teachCode), after);
        } else {
            skip("reyes teaches no course in the seed data");
        }

        section("Permissions (rules enforced by the services)");
        Res studentUpload = post("/api/grades", gradeBody(teachCode == null ? "STDISCM" : teachCode, "E2E-TEST", ALICE_ID, 4.0), alice);
        check("student calls UploadGrades -> PERMISSION_DENIED", studentUpload.status == 403 && studentUpload.body.contains("PERMISSION_DENIED"), studentUpload);
        Res notMine = post("/api/grades", gradeBody("NOT-MY-COURSE", "E2E-TEST", ALICE_ID, 4.0), reyes);
        check("faculty uploads for a course they don't teach -> PERMISSION_DENIED", notMine.status == 403 && notMine.body.contains("PERMISSION_DENIED"), notMine);

        section("Logout");
        String temp = login("carol");
        Res out = post("/api/auth/logout", "{}", temp);
        check("logout", out.status == 204, out);
        Res reuse = get("/api/courses", temp);
        check("reusing the old cookie after logout -> 401 (sent to login page)", reuse.status == 401, reuse);

        writeSessions(alice, reyes);
        System.out.println("\nSaved alice's and reyes's sessions to " + SESSIONS + " for the node-down tests (valid ~15 min).");
    }

    static void authDown() throws Exception {
        String[] s = readSessions();
        section("Auth node stopped");
        Res login = post("/api/auth/login", json("username", "bob", "password", PASSWORD), null);
        check("new login fails (503, auth unavailable)", login.status == 503 && login.body.contains("auth"), login);
        check("logged-in student still views courses", get("/api/courses", s[0]).status == 200, null);
        check("logged-in student still views grades", get("/api/grades", s[0]).status == 200, null);
        check("logged-in faculty still sees their courses", get("/api/grades/teaching", s[1]).status == 200, null);
    }

    static void courseDown() throws Exception {
        String[] s = readSessions();
        section("Course node stopped");
        Res c = get("/api/courses", s[0]);
        check("course list -> unavailable (503, course)", c.status == 503 && c.body.contains("course"), c);
        Res e = get("/api/enrollments", s[0]);
        check("my enrollments -> unavailable", e.status == 503, e);
        check("grades still work", get("/api/grades", s[0]).status == 200, null);
        Res teaching = get("/api/grades/teaching", s[1]);
        check("faculty's course list (from grade node) still works", teaching.status == 200, teaching);
        String code = field(teaching.body, "courseCode");
        if (code != null) {
            Res up = post("/api/grades", gradeBody(code, "E2E-COURSE-DOWN", BOB_ID, 3.0), s[1]);
            check("grade upload by typed student ID still works", up.status == 200, up);
        } else {
            skip("reyes teaches no course");
        }
    }

    static void gradeDown() throws Exception {
        String[] s = readSessions();
        section("Grade node stopped");
        Res g = get("/api/grades", s[0]);
        check("my grades -> unavailable (503, grade)", g.status == 503 && g.body.contains("grade"), g);
        Res t = get("/api/grades/teaching", s[1]);
        check("faculty upload page -> unavailable", t.status == 503, t);
        check("course list still works", get("/api/courses", s[0]).status == 200, null);
        check("my enrollments still work", get("/api/enrollments", s[0]).status == 200, null);
    }

    static void recovered() throws Exception {
        String alice = login("alice");
        String reyes = login("reyes");
        section("All nodes back");
        check("courses", get("/api/courses", alice).status == 200, null);
        check("enrollments", get("/api/enrollments", alice).status == 200, null);
        check("grades", get("/api/grades", alice).status == 200, null);
        check("faculty courses", get("/api/grades/teaching", reyes).status == 200, null);
        check("announcements", get("/api/announcements", alice).status == 200, null);
        writeSessions(alice, reyes);
    }

    static void saveSessions() throws Exception {
        writeSessions(login("alice"), login("reyes"));
        System.out.println("Saved sessions to " + SESSIONS);
    }

    // ------------------------------------------------------------------ helpers

    record Res(int status, String body, String cookie) { }

    record Course(String code, int capacity, int enrolled, boolean open) {
        static List<Course> parseAll(String json) {
            List<Course> out = new ArrayList<>();
            Matcher m = Pattern.compile("\\{[^{}]*}").matcher(json);
            while (m.find()) {
                String o = m.group();
                String code = field(o, "code");
                if (code == null) continue;
                out.add(new Course(code, num(o, "capacity"), num(o, "enrolled"), o.contains("\"open\":true")));
            }
            return out;
        }
    }

    static String login(String user) throws Exception {
        Res r = post("/api/auth/login", json("username", user, "password", PASSWORD), null);
        if (r.status != 200 || r.cookie == null) {
            System.out.println("FAIL  could not log in as " + user + ": " + r.status + " " + r.body);
            System.exit(1);
        }
        return r.cookie;
    }

    static Res get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), token);
    }

    static Res post(String path, String body, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), token);
    }

    static Res send(HttpRequest.Builder b, String token) throws Exception {
        b.timeout(Duration.ofSeconds(10));
        if (token != null) b.header("Cookie", "token=" + token);
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            String cookie = r.headers().allValues("set-cookie").stream()
                    .filter(c -> c.startsWith("token=") && !c.startsWith("token=;"))
                    .map(c -> c.substring(6, c.indexOf(';') < 0 ? c.length() : c.indexOf(';')))
                    .findFirst().orElse(null);
            return new Res(r.statusCode(), r.body(), cookie);
        } catch (IOException e) {
            return new Res(0, "connection failed: " + e.getMessage(), null);
        }
    }

    static void section(String title) {
        System.out.println("-- " + title);
    }

    static void check(String name, boolean ok, Res r) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name + (r == null ? "" : "   [" + r.status + " " + truncate(r.body) + "]"));
        }
    }

    static void skip(String why) {
        skipped++;
        System.out.println("SKIP  " + why);
    }

    static String truncate(String s) {
        s = s.replaceAll("\\s+", " ");
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }

    static String json(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kv[i]).append("\":\"").append(kv[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    static String gradeBody(String course, String term, String studentId, double grade) {
        return "{\"courseCode\":\"" + course + "\",\"term\":\"" + term + "\",\"grades\":[{\"studentId\":\""
                + studentId + "\",\"grade\":" + grade + "}]}";
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static int num(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static void writeSessions(String student, String faculty) throws IOException {
        Files.writeString(SESSIONS, student + "\n" + faculty + "\n");
    }

    static String[] readSessions() throws IOException {
        if (!Files.exists(SESSIONS)) {
            System.out.println("No saved sessions. Run `functional` or `login` mode BEFORE stopping a node.");
            System.exit(2);
        }
        return Files.readString(SESSIONS).trim().split("\n");
    }
}
