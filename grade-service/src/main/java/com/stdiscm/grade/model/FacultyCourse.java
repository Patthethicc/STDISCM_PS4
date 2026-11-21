package com.stdiscm.grade.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** One row of {@code faculty_courses}: this faculty member teaches this course. */
@Entity
@Table(name = "faculty_courses")
public class FacultyCourse {

    @EmbeddedId
    private Key id;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    protected FacultyCourse() { }

    public UUID getFacultyId() { return id.facultyId; }
    public String getCourseCode() { return id.courseCode; }
    public String getCourseTitle() { return courseTitle; }

    /** Primary key (faculty_id, course_code). */
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "faculty_id", nullable = false)
        private UUID facultyId;

        @Column(name = "course_code", nullable = false)
        private String courseCode;

        protected Key() { }

        public Key(UUID facultyId, String courseCode) {
            this.facultyId = facultyId;
            this.courseCode = courseCode;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(facultyId, k.facultyId)
                    && Objects.equals(courseCode, k.courseCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(facultyId, courseCode);
        }
    }
}
