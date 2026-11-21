package com.stdiscm.grade.repository;

import com.stdiscm.grade.model.FacultyCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FacultyCourseRepository extends JpaRepository<FacultyCourse, FacultyCourse.Key> {

    @Query("select f from FacultyCourse f where f.id.facultyId = :facultyId order by f.id.courseCode")
    List<FacultyCourse> findTaughtBy(@Param("facultyId") UUID facultyId);
}
