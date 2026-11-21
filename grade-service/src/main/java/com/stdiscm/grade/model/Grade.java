package com.stdiscm.grade.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code grades}. Read through JPA; written only by
 * {@link com.stdiscm.grade.repository.GradeBatchWriter} (batched INSERT ... ON CONFLICT DO UPDATE),
 * hence {@code @Immutable}. Course code and title are stored here so the grade service never
 * needs the course service.
 */
@Entity
@Immutable
@Table(name = "grades")
public class Grade {

    @Id
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    @Column(nullable = false)
    private String term;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal grade;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_by_name", nullable = false)
    private String uploadedByName;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected Grade() { }

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public String getCourseCode() { return courseCode; }
    public String getCourseTitle() { return courseTitle; }
    public String getTerm() { return term; }
    public BigDecimal getGrade() { return grade; }
    public UUID getUploadedBy() { return uploadedBy; }
    public String getUploadedByName() { return uploadedByName; }
    public Instant getUploadedAt() { return uploadedAt; }
}
