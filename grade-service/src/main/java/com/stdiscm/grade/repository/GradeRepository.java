package com.stdiscm.grade.repository;

import com.stdiscm.grade.model.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Reads only. Writes go through {@link GradeBatchWriter}. */
public interface GradeRepository extends JpaRepository<Grade, UUID> {

    /** Unordered by term here: GradeService sorts newest term first (terms like "T1 2026-2027" don't sort as text). */
    @Query("select g from Grade g where g.studentId = :studentId order by g.courseCode")
    List<Grade> findForStudent(@Param("studentId") UUID studentId);
}
