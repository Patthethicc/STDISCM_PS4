import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grades API checks and database-failover checks, run against the website like a browser would.
 *
 *   java tests/e2e/ApiAndFailoverTest.java grades  [http://localhost]
 *   java tests/e2e/ApiAndFailoverTest.java db-down [http://localhost] <grade|course|auth>
 *   java tests/e2e/ApiAndFailoverTest.java db-up   [http://localhost] <grade|course|auth>
 *
 * grades   : own-grades-only, faculty sees only own courses, batch validation, update-existing.
 * db-down  : run while that service's PRIMARY database is stopped: reads still work, writes are refused (503).
 * db-up    : run after the primary is back: writes work again.
 * Exit code 0 = every check passed.
 */
public class ApiAndFailoverTest {

    static final String PASSWORD = "password123";
    static final String ALICE = "11111111-1111-1111-1111-111111111111";
    static final String BOB = "11111111-1111-1111-1111-111111111112";
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static String base;
    static int passed = 0, failed = 0;

    record Res(int status, String body, String cookie) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: java tests/e2e/ApiAndFailoverTest.java <grades|db-down|db-up> [http://localhost] [grade|course|auth]");
            System.exit(2);
        }
        base = (args.length > 1 ? args[1] : "http://localhost").replaceAll("/+$", "");
        String which = args.length > 2 ? args[2] : "";
        System.out.println("== " + args[0] + " " + which + " against " + base + "\n");
        switch (args[0]) {
            case "grades" -> grades();
            case "db-down" -> dbDown(which);
            case "db-up" -> dbUp(which);
            default -> { System.out.println("unknown mode: " + args[0]); System.exit(2); }
        }
        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    static void grades() throws Exception {
        String alice = login("alice"), bob = login("bob"), reyes = login("reyes");
        if (alice == null || bob == null || reyes == null) { check("log in as alice, bob and reyes", false, null); return; }

        System.out.println("-- Students see only their own grades");
        Res a = get("/api/grades", alice);
        check("alice sees CCPROG3 and CSARCH2", a.status == 200 && a.body.contains("\"CCPROG3\"") && a.body.contains("\"CSARCH2\""), a);
        check("alice's CSARCH2 is 4.0", Double.valueOf(4.0).equals(gradeOf(entry(a.body, "CSARCH2", "T3 2025-2026"))), a);
        Res b = get("/api/grades", bob);
        check("bob sees CCPROG3 and CSARCH2 but not carol's CSOPESY",
                b.status == 200 && b.body.contains("\"CCPROG3\"") && b.body.contains("\"CSARCH2\"") && !b.body.contains("CSOPESY"), b);
        check("bob's CSARCH2 is 3.0 (not alice's 4.0)", Double.valueOf(3.0).equals(gradeOf(entry(b.body, "CSARCH2", "T3 2025-2026"))), b);
        Res spoof = get("/api/grades?studentId=" + ALICE, bob);
        check("bob cannot request alice's grades by ID", Double.valueOf(3.0).equals(gradeOf(entry(spoof.body, "CSARCH2", "T3 2025-2026"))), spoof);

        System.out.println("-- Faculty sees only the courses they teach");
        Res t = get("/api/grades/teaching", reyes);
        check("reyes teaches STDISCM and CS101 only",
                t.status == 200 && t.body.contains("\"STDISCM\"") && t.body.contains("\"CS101\"")
                        && !t.body.contains("FULL101") && !t.body.contains("CLOSED01"), t);

        System.out.println("-- Batch validation");
        Res bad = post("/api/grades", batch("STDISCM", "API-TEST-BAD", ALICE, 3.5, BOB, 4.7), reyes);
        check("batch containing 4.7 is rejected (4xx)", bad.status >= 400 && bad.status < 500 && bad.status != 401 && bad.status != 403, bad);
        check("nothing from the rejected batch was saved", !get("/api/grades", alice).body.contains("API-TEST-BAD"), null);

        System.out.println("-- Valid batch, then update an existing grade");
        Res ok = post("/api/grades", batch("STDISCM", "API-TEST", ALICE, 3.5, BOB, 3.0), reyes);
        check("batch of two valid grades accepted", ok.status == 200, ok);
        Res again = post("/api/grades", batch("STDISCM", "API-TEST", ALICE, 4.0, BOB, 3.0), reyes);
        check("same student/course/term uploaded again is accepted", again.status == 200, again);
        Res a2 = get("/api/grades", alice);
        check("alice has exactly one STDISCM API-TEST row (updated, not duplicated)", entries(a2.body, "STDISCM", "API-TEST") == 1, a2);
        check("and it is now 4.0", Double.valueOf(4.0).equals(gradeOf(entry(a2.body, "STDISCM", "API-TEST"))), a2);
        Res b2 = get("/api/grades", bob);
        check("bob's grade for the same batch is 3.0", Double.valueOf(3.0).equals(gradeOf(entry(b2.body, "STDISCM", "API-TEST"))), b2);
    }

    static void dbDown(String which) throws Exception {
        switch (which) {
            case "grade" -> {
                String alice = need("alice"), reyes = need("reyes");
                Res r = get("/api/grades", alice);
                check("grades still readable (from the replica)", r.status == 200 && r.body.contains("\"CCPROG3\""), r);
                Res w = post("/api/grades", batch("STDISCM", "API-TEST-DBDOWN", ALICE, 3.5), reyes);
                check("upload refused with 503 while the primary is down", w.status == 503, w);
            }
            case "course" -> {
                String alice = need("alice"), carol = need("carol");
                Res r = get("/api/courses", alice);
                check("course list still readable (from the replica)", r.status == 200 && r.body.contains("\"STDISCM\""), r);
                Res w = post("/api/courses/CS101/enroll", "{}", carol);
                check("enroll refused with 503 while the primary is down", w.status == 503, w);
            }
            case "auth" -> {
                Res l = post("/api/auth/login", loginBody("alice"), null);
                check("login still works (users read from the replica)", l.status == 200 && l.cookie != null, l);
            }
            default -> { System.out.println("which must be grade, course or auth"); System.exit(2); }
        }
    }

    static void dbUp(String which) throws Exception {
        switch (which) {
            case "grade" -> {
                Res w = post("/api/grades", batch("STDISCM", "API-TEST-DBUP", ALICE, 3.5), need("reyes"));
                check("upload works again after failback", w.status == 200, w);
            }
            case "course" -> {
                Res w = post("/api/courses/CS101/enroll", "{}", need("carol"));
                check("enroll works again after failback", w.status == 200 || (w.status == 409 && w.body.contains("ALREADY_ENROLLED")), w);
            }
            case "auth" -> {
                Res l = post("/api/auth/login", loginBody("carol"), null);
                check("login works", l.status == 200 && l.cookie != null, l);
                if (l.cookie != null) {
                    Res out = post("/api/auth/logout", "{}", l.cookie);
                    check("logout (a write) works again after failback", out.status == 204, out);
                }
            }
            default -> { System.out.println("which must be grade, course or auth"); System.exit(2); }
        }
    }

    // ------------------------------------------------------------------ helpers

    static String need(String user) throws Exception {
        String c = login(user);
        if (c == null) { check("log in as " + user, false, null); System.out.printf("%n%d passed, %d failed%n", passed, failed); System.exit(1); }
        return c;
    }

    static String login(String user) throws Exception {
        return post("/api/auth/login", loginBody(user), null).cookie;
    }

    static String loginBody(String user) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    static String batch(String course, String term, Object... studentGrade) {
        StringBuilder sb = new StringBuilder("{\"courseCode\":\"" + course + "\",\"term\":\"" + term + "\",\"grades\":[");
        for (int i = 0; i < studentGrade.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append("{\"studentId\":\"").append(studentGrade[i]).append("\",\"grade\":").append(studentGrade[i + 1]).append('}');
        }
        return sb.append("]}").toString();
    }

    /** The one JSON object in a grades array for this course and term, or "". */
    static String entry(String body, String course, String term) {
        Matcher m = Pattern.compile("\\{[^{}]*\"courseCode\"\\s*:\\s*\"" + Pattern.quote(course) + "\"[^{}]*\"term\"\\s*:\\s*\"" + Pattern.quote(term) + "\"[^{}]*\\}").matcher(body);
        return m.find() ? m.group() : "";
    }

    static Double gradeOf(String entry) {
        Matcher m = Pattern.compile("\"grade\"\\s*:\\s*(-?[0-9.]+)").matcher(entry);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }

    static int entries(String body, String course, String term) {
        Matcher m = Pattern.compile("\\{[^{}]*\"courseCode\"\\s*:\\s*\"" + Pattern.quote(course) + "\"[^{}]*\"term\"\\s*:\\s*\"" + Pattern.quote(term) + "\"[^{}]*\\}").matcher(body);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    static Res get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), token);
    }

    static Res post(String path, String body, String token) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
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

    static void check(String name, boolean ok, Res r) {
        if (ok) { passed++; System.out.println("PASS  " + name); return; }
        failed++;
        String body = r == null ? "" : r.body.replaceAll("\\s+", " ");
        System.out.println("FAIL  " + name + (r == null ? "" : "   [" + r.status + " " + (body.length() > 160 ? body.substring(0, 160) + "..." : body) + "]"));
    }
}
