package com.stdiscm.grade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Input rules for the grade service, with no Spring or gRPC types so they are easy to test.
 * Every problem in an upload is reported at once, so faculty can fix it in one go.
 */
public final class GradeRules {

    public static final int MAX_BATCH = 500;
    static final int MAX_PROBLEMS_SHOWN = 10;
    static final int MAX_CODE = 20;
    static final int MAX_TERM = 20;

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** "T1 2026-2027" (the upload page's format) or "AY2026-2027 T1": used only to sort newest first. */
    private static final Pattern TERM_T_FIRST = Pattern.compile("T([1-3])\\s+(\\d{4})-\\d{4}");
    private static final Pattern TERM_AY_FIRST = Pattern.compile("AY\\s*(\\d{4})-\\d{4}\\s+T([1-3])");

    /** A row as it arrived in the request. */
    public record RawEntry(String studentId, double grade) { }

    /** A row that passed every check. */
    public record ValidEntry(UUID studentId, BigDecimal grade) { }

    private GradeRules() { }

    /** Trimmed, upper-case course code, e.g. STDISCM. Whether the caller teaches it is checked separately. */
    public static String courseCode(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase();
        if (code.isEmpty()) throw new InvalidRequestException("course_code is required");
        if (code.length() > MAX_CODE) throw new InvalidRequestException("course_code is longer than " + MAX_CODE + " characters");
        return code;
    }

    /** Term as typed on the upload page (e.g. "T1 2026-2027"), trimmed, single-spaced, upper-case. */
    public static String term(String raw) {
        String term = raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toUpperCase();
        if (term.isEmpty()) throw new InvalidRequestException("term is required");
        if (term.length() > MAX_TERM) throw new InvalidRequestException("term is longer than " + MAX_TERM + " characters");
        return term;
    }

    /** Allowed: 0.0 to 4.0 in steps of 0.5. Returns the problem text, or null when fine. */
    static String gradeProblem(double g) {
        if (Double.isNaN(g) || Double.isInfinite(g)) return "grade is not a number";
        if (g < 0.0 || g > 4.0) return "grade " + g + " is outside 0.0 to 4.0";
        if (g * 2 != Math.rint(g * 2)) return "grade " + g + " is not a multiple of 0.5";
        return null;
    }

    /** 3.5 -> BigDecimal 3.5 (scale 1), exact because halves are exact in binary. */
    public static BigDecimal grade(double g) {
        String problem = gradeProblem(g);
        if (problem != null) throw new InvalidRequestException(problem);
        return BigDecimal.valueOf(g).setScale(1, RoundingMode.UNNECESSARY);
    }

    /**
     * Checks a whole upload. All-or-nothing: if any row is bad, nothing is saved and the
     * exception lists the bad rows (1-based, in request order).
     */
    public static List<ValidEntry> batch(List<RawEntry> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidRequestException("the upload has no grades");
        }
        if (raw.size() > MAX_BATCH) {
            throw new InvalidRequestException("at most " + MAX_BATCH + " grades per upload, got " + raw.size());
        }

        List<String> problems = new ArrayList<>();
        List<ValidEntry> rows = new ArrayList<>(raw.size());
        Map<UUID, Integer> firstRow = new HashMap<>();

        for (int i = 0; i < raw.size(); i++) {
            int row = i + 1;
            RawEntry e = raw.get(i);
            String id = e.studentId() == null ? "" : e.studentId().trim();

            UUID studentId = null;
            if (!UUID_TEXT.matcher(id).matches()) {
                problems.add("row " + row + ": student_id '" + id + "' is not a valid ID");
            } else {
                studentId = UUID.fromString(id);
                Integer earlier = firstRow.putIfAbsent(studentId, row);
                if (earlier != null) {
                    problems.add("row " + row + ": student " + id + " already appears in row " + earlier);
                }
            }

            String gradeProblem = gradeProblem(e.grade());
            if (gradeProblem != null) {
                problems.add("row " + row + ": " + gradeProblem);
            }

            if (studentId != null && gradeProblem == null) {
                rows.add(new ValidEntry(studentId, grade(e.grade())));
            }
        }

        if (!problems.isEmpty()) {
            throw new InvalidRequestException(problems, MAX_PROBLEMS_SHOWN);
        }
        return rows;
    }

    /**
     * Sort key for "newest term first": year * 10 + term number for "T1 2026-2027" or
     * "AY2026-2027 T1"; any other text (e.g. a test term) sorts after the known ones.
     */
    public static int termOrder(String term) {
        String t = term == null ? "" : term.trim().toUpperCase();
        Matcher m = TERM_T_FIRST.matcher(t);
        if (m.matches()) return Integer.parseInt(m.group(2)) * 10 + Integer.parseInt(m.group(1));
        m = TERM_AY_FIRST.matcher(t);
        if (m.matches()) return Integer.parseInt(m.group(1)) * 10 + Integer.parseInt(m.group(2));
        return -1;
    }

    /** Newest known term first, unknown terms last (alphabetical), then course code. */
    public static <T> Comparator<T> newestTermFirst(java.util.function.Function<T, String> term,
                                                    java.util.function.Function<T, String> code) {
        return Comparator.<T>comparingInt(x -> termOrder(term.apply(x))).reversed()
                .thenComparing(term)
                .thenComparing(code);
    }
}
