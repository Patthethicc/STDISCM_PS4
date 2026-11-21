package com.stdiscm.course.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * A student's seat in a course. The database unique constraint on
 * (student_id, course_code) is the final guard against duplicates when two
 * enrollment requests race; the service turns that violation into
 * ALREADY_ENROLLED.
 */
@Entity
@Table(
        name = "enrollment",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_enrollment_student_course",
                columnNames = {"student_id", "course_code"}))
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_code", nullable = false, length = 20)
    private String courseCode;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    protected Enrollment() { }

    public Enrollment(UUID studentId, String courseCode, String studentName) {
        this.studentId = studentId;
        this.courseCode = courseCode;
        this.studentName = studentName;
        this.enrolledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public String getCourseCode() { return courseCode; }
    public String getStudentName() { return studentName; }
    public Instant getEnrolledAt() { return enrolledAt; }
}
