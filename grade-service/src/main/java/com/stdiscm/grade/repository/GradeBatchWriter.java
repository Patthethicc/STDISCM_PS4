package com.stdiscm.grade.repository;

import com.stdiscm.grade.model.FacultyCourse;
import com.stdiscm.grade.service.GradeRules.ValidEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Saves an upload as JDBC batches of one upsert each: a new (student, course, term) is inserted,
 * an existing one gets the new grade, uploader and time. Runs inside the caller's transaction,
 * so the whole upload commits or none of it does.
 */
@Repository
public class GradeBatchWriter {

    static final int BATCH_SIZE = 100;

    static final String UPSERT = """
            INSERT INTO grades (student_id, course_code, course_title, term, grade,
                                uploaded_by, uploaded_by_name, uploaded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (student_id, course_code, term) DO UPDATE
               SET grade            = EXCLUDED.grade,
                   course_title     = EXCLUDED.course_title,
                   uploaded_by      = EXCLUDED.uploaded_by,
                   uploaded_by_name = EXCLUDED.uploaded_by_name,
                   uploaded_at      = now()
            """;

    private final JdbcTemplate jdbc;

    public GradeBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(FacultyCourse course, String term, List<ValidEntry> rows, UUID facultyId, String facultyName) {
        jdbc.batchUpdate(UPSERT, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, row.studentId());
            ps.setString(2, course.getCourseCode());
            ps.setString(3, course.getCourseTitle());   // title comes from faculty_courses, not the client
            ps.setString(4, term);
            ps.setBigDecimal(5, row.grade());
            ps.setObject(6, facultyId);
            ps.setString(7, facultyName);
        });
    }
}
