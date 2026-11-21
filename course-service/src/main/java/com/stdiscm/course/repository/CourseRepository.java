package com.stdiscm.course.repository;

import com.stdiscm.course.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, String> {

    List<Course> findAllByOrderByCourseCodeAsc();

    /**
     * Atomically reserves one seat. The WHERE clause is the concurrency guard:
     * under READ COMMITTED, PostgreSQL re-checks it after waiting for a
     * concurrent update, so the last free seat is never sold twice.
     *
     * @return 1 when a seat was reserved, 0 when the course is missing, closed
     *         or full (the caller then looks up the exact reason)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE course
               SET enrolled_count = enrolled_count + 1
             WHERE code = :code
               AND is_open = TRUE
               AND enrolled_count < capacity
            """, nativeQuery = true)
    int reserveSeat(@Param("code") String code);
}
