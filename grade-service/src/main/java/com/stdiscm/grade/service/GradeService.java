package com.stdiscm.grade.service;

import com.stdiscm.grade.model.FacultyCourse;
import com.stdiscm.grade.model.Grade;
import com.stdiscm.grade.repository.FacultyCourseRepository;
import com.stdiscm.grade.repository.GradeBatchWriter;
import com.stdiscm.grade.repository.GradeRepository;
import com.stdiscm.grade.service.GradeRules.RawEntry;
import com.stdiscm.grade.service.GradeRules.ValidEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Business rules for grades. Callers pass IDs taken from the verified token, never from the
 * request. No calls to other services: course titles come from faculty_courses.
 */
@Service
public class GradeService {

    private final GradeRepository grades;
    private final FacultyCourseRepository teaching;
    private final GradeBatchWriter writer;

    public GradeService(GradeRepository grades, FacultyCourseRepository teaching, GradeBatchWriter writer) {
        this.grades = grades;
        this.teaching = teaching;
        this.writer = writer;
    }

    /** A student's own grades, newest term first. */
    @Transactional(readOnly = true)
    public List<Grade> gradesOf(UUID studentId) {
        List<Grade> list = new ArrayList<>(grades.findForStudent(studentId));
        list.sort(GradeRules.newestTermFirst(Grade::getTerm, Grade::getCourseCode));
        return list;
    }

    @Transactional(readOnly = true)
    public List<FacultyCourse> coursesTaughtBy(UUID facultyId) {
        return teaching.findTaughtBy(facultyId);
    }

    /**
     * One upload = one transaction. Order: course code and term, then "do you teach this course",
     * then every row; only then write. Any failure saves nothing. Returns how many grades were saved
     * (inserted or updated).
     */
    @Transactional
    public int upload(UUID facultyId, String facultyName, String rawCourseCode, String rawTerm,
                      List<RawEntry> entries) {
        String code = GradeRules.courseCode(rawCourseCode);
        String term = GradeRules.term(rawTerm);
        FacultyCourse course = teaching.findById(new FacultyCourse.Key(facultyId, code))
                .orElseThrow(() -> new NotTeachingException(code));
        List<ValidEntry> rows = GradeRules.batch(entries);

        writer.upsert(course, term, rows, facultyId, facultyName);
        return rows.size();
    }
}
