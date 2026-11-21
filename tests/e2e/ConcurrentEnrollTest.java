import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 20 students try to enroll in the same course at the same instant.
 * The course service must let in exactly as many as there are free slots (the doc's case:
 * 5 slots left -> exactly 5 succeed), never overbooking.
 *
 *   java tests/e2e/ConcurrentEnrollTest.java <COURSE_CODE> [http://VIEW_IP]
 *
 * Uses the 20 test students load01..load20 (db/auth/seed-loadtest.sql). To run it again,
 * reset the course DB first: docker compose --profile course down -v && ... up -d
 */
public class ConcurrentEnrollTest {

    static final int STUDENTS = 20;
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static String base;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: java tests/e2e/ConcurrentEnrollTest.java <COURSE_CODE> [http://VIEW_IP]");
            System.exit(2);
        }
        String code = args[0];
        base = (args.length > 1 ? args[1] : "http://localhost").replaceAll("/+$", "");

        // Log everyone in first, so only the enroll calls race.
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= STUDENTS; i++) {
            tokens.add(login(String.format("load%02d", i)));
        }

        int[] before = courseState(tokens.get(0), code);
        int free = before[0] - before[1];
        int expected = Math.max(0, Math.min(free, STUDENTS));
        System.out.printf("%s before: %d/%d enrolled, %d free slots -> expect exactly %d successes%n",
                code, before[1], before[0], free, expected);

        CountDownLatch ready = new CountDownLatch(STUDENTS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        Map<String, AtomicInteger> outcomes = new ConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>();

        for (String token : tokens) {
            Thread t = Thread.ofPlatform().start(() -> {
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/courses/" + code + "/enroll"))
                            .header("Cookie", "token=" + token)
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(15))
                            .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
                    ready.countDown();
                    go.await();
                    HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
                    String key = r.statusCode() == 200 ? "OK" : r.statusCode() + " " + field(r.body(), "error");
                    if (r.statusCode() == 200) ok.incrementAndGet();
                    outcomes.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                } catch (Exception e) {
                    outcomes.computeIfAbsent("exception " + e.getClass().getSimpleName(), k -> new AtomicInteger()).incrementAndGet();
                }
            });
            threads.add(t);
        }
        ready.await();
        long start = System.nanoTime();
        go.countDown();
        for (Thread t : threads) t.join();
        long ms = (System.nanoTime() - start) / 1_000_000;

        int[] after = courseState(tokens.get(0), code);
        System.out.printf("%d requests in %d ms: %s%n", STUDENTS, ms, new TreeMap<>(outcomes));
        System.out.printf("%s after: %d/%d enrolled%n", code, after[1], after[0]);

        boolean exact = ok.get() == expected;
        boolean noOverbook = after[1] <= after[0];
        boolean countMatches = after[1] == before[1] + ok.get();
        System.out.println((exact ? "PASS" : "FAIL") + "  exactly " + expected + " succeeded (got " + ok.get() + ")");
        System.out.println((noOverbook ? "PASS" : "FAIL") + "  no overbooking (" + after[1] + " <= " + after[0] + ")");
        System.out.println((countMatches ? "PASS" : "FAIL") + "  enrolled count went up by exactly the number of successes");
        System.exit(exact && noOverbook && countMatches ? 0 : 1);
    }

    static String login(String user) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + user + "\",\"password\":\"password123\"}"))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        String cookie = r.headers().allValues("set-cookie").stream()
                .filter(c -> c.startsWith("token=")).findFirst().orElse(null);
        if (r.statusCode() != 200 || cookie == null) {
            System.out.println("could not log in as " + user + " (" + r.statusCode() + "). Is db/auth/seed-loadtest.sql loaded?");
            System.exit(2);
        }
        return cookie.substring(6, cookie.indexOf(';') < 0 ? cookie.length() : cookie.indexOf(';'));
    }

    /** Returns {capacity, enrolled} for the course. */
    static int[] courseState(String token, String code) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/courses"))
                .header("Cookie", "token=" + token).GET().build();
        String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        Matcher m = Pattern.compile("\\{[^{}]*\"code\"\\s*:\\s*\"" + Pattern.quote(code) + "\"[^{}]*}").matcher(body);
        if (!m.find()) {
            System.out.println("course " + code + " not found in /api/courses");
            System.exit(2);
        }
        String o = m.group();
        return new int[] { num(o, "capacity"), num(o, "enrolled") };
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "?";
    }

    static int num(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
