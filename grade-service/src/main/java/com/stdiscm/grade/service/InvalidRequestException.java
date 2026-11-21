package com.stdiscm.grade.service;

import java.util.List;

/** Bad input. Sent to the caller as INVALID_ARGUMENT (see GradeExceptionAdvice). */
public class InvalidRequestException extends RuntimeException {

    private final List<String> problems;

    public InvalidRequestException(String problem) {
        super(problem);
        this.problems = List.of(problem);
    }

    /** Several problems; the message shows the first {@code shown} and counts the rest. */
    public InvalidRequestException(List<String> problems, int shown) {
        super(summary(problems, shown));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }

    private static String summary(List<String> problems, int shown) {
        String head = problems.size() == 1 ? "1 problem, nothing saved: " : problems.size() + " problems, nothing saved: ";
        String listed = String.join("; ", problems.subList(0, Math.min(shown, problems.size())));
        int more = problems.size() - shown;
        return head + listed + (more > 0 ? "; and " + more + " more" : "");
    }
}
