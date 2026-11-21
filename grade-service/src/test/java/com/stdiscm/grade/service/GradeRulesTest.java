package com.stdiscm.grade.service;

import com.stdiscm.grade.service.GradeRules.RawEntry;
import com.stdiscm.grade.service.GradeRules.ValidEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeRulesTest {

    static final String ALICE = "11111111-1111-1111-1111-111111111111";
    static final String BOB = "11111111-1111-1111-1111-111111111112";

    @Test
    void acceptsEveryHalfStepFromZeroToFour() {
        for (double g : new double[] {0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0}) {
            assertEquals(1, GradeRules.grade(g).scale(), "grade " + g);
        }
        assertEquals("4.0", GradeRules.grade(4).toPlainString());
        assertEquals("3.5", GradeRules.grade(3.5).toPlainString());
    }

    @Test
    void rejectsBadGrades() {
        for (double g : new double[] {3.7, 4.5, -0.5, 5, 1.25, 10, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(InvalidRequestException.class, () -> GradeRules.grade(g), "should reject " + g);
        }
    }

    @Test
    void courseCodeAndTermAreNormalisedButFreeForm() {
        assertEquals("STDISCM", GradeRules.courseCode("  stdiscm "));
        assertEquals("NOT-MY-COURSE", GradeRules.courseCode("NOT-MY-COURSE"));     // permission is checked later
        assertEquals("T1 2026-2027", GradeRules.term(" t1   2026-2027 "));
        assertEquals("E2E-TEST", GradeRules.term("E2E-TEST"));                     // the e2e tests use free-form terms
        assertThrows(InvalidRequestException.class, () -> GradeRules.courseCode(" "));
        assertThrows(InvalidRequestException.class, () -> GradeRules.courseCode("X".repeat(21)));
        assertThrows(InvalidRequestException.class, () -> GradeRules.term(""));
        assertThrows(InvalidRequestException.class, () -> GradeRules.term("T".repeat(21)));
    }

    @Test
    void validBatchPassesThrough() {
        List<ValidEntry> rows = GradeRules.batch(List.of(new RawEntry(ALICE, 3.5), new RawEntry(BOB, 2)));
        assertEquals(2, rows.size());
        assertEquals("3.5", rows.get(0).grade().toPlainString());
        assertEquals("2.0", rows.get(1).grade().toPlainString());
    }

    @Test
    void badBatchListsEveryProblemWithRowNumbers() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> GradeRules.batch(List.of(
                new RawEntry(ALICE, 3.5),
                new RawEntry("not-an-id", 3.0),
                new RawEntry(BOB, 3.7),
                new RawEntry(ALICE, 4.0))));
        List<String> p = e.problems();
        assertEquals(3, p.size(), p.toString());
        assertTrue(p.get(0).startsWith("row 2: student_id"), p.get(0));
        assertTrue(p.get(1).startsWith("row 3: grade 3.7"), p.get(1));
        assertTrue(p.get(2).contains("already appears in row 1"), p.get(2));
        assertTrue(e.getMessage().startsWith("3 problems, nothing saved"), e.getMessage());
    }

    @Test
    void emptyAndOversizedBatchesAreRejected() {
        assertThrows(InvalidRequestException.class, () -> GradeRules.batch(List.of()));
        assertThrows(InvalidRequestException.class, () -> GradeRules.batch(null));
        List<RawEntry> big = new ArrayList<>(Collections.nCopies(GradeRules.MAX_BATCH + 1, new RawEntry(ALICE, 1.0)));
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> GradeRules.batch(big));
        assertTrue(e.getMessage().contains("at most 500"));
    }

    @Test
    void longProblemListIsTruncatedInTheMessage() {
        List<RawEntry> rows = new ArrayList<>();
        for (int i = 0; i < 15; i++) rows.add(new RawEntry("x" + i, 1.0));
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> GradeRules.batch(rows));
        assertEquals(15, e.problems().size());
        assertTrue(e.getMessage().endsWith("and 5 more"), e.getMessage());
    }

    @Test
    void termsSortNewestFirstWithUnknownTermsLast() {
        List<String> terms = new ArrayList<>(List.of("T2 2025-2026", "E2E-TEST", "T1 2026-2027", "T3 2025-2026", "AY2026-2027 T2"));
        terms.sort(GradeRules.newestTermFirst(t -> t, t -> ""));
        assertEquals(List.of("AY2026-2027 T2", "T1 2026-2027", "T3 2025-2026", "T2 2025-2026", "E2E-TEST"), terms);
    }
}
