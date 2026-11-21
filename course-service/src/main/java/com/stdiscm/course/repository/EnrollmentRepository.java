package com.stdiscm.course.repository;

import com.stdiscm.course.model.Course;
import com.stdiscm.course.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    /** Roster order: by student name, so faculty see a stable list. */
    List<Enrollment> findByCourseCodeOrderByStudentNameAsc(String courseCode);

    /** The caller's own courses, ordered by code. */
    @Query(value = """
            SELECT c.*
              FROM course c
              JOIN enrollment e ON e.course_code = c.code
             WHERE e.student_id = :studentId
             ORDER BY c.code
            """, nativeQuery = true)
    List<Course> findCoursesForStudent(@Param("studentId") UUID studentId);
}
