package com.stdiscm.course.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One course offering. {@code enrolledCount} is maintained by the conditional
 * UPDATE in {@link com.stdiscm.course.repository.CourseRepository#reserveSeat},
 * never by a read-then-write in Java.
 */
@Entity
@Table(name = "course")
public class Course {

    @Id
    @Column(name = "code", nullable = false, length = 20)
    private String courseCode;

    @Column(name = "title", nullable = false, length = 200)
    private String courseTitle;

    @Column(name = "capacity", nullable = false)
    private int courseCapacity;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Column(name = "is_open", nullable = false)
    private boolean isOpen;

    protected Course() { }

    public String getCourseCode() { return courseCode; }
    public String getCourseTitle() { return courseTitle; }
    public int getCourseCapacity() { return courseCapacity; }
    public int getEnrolledCount() { return enrolledCount; }
    public boolean isOpen() { return isOpen; }
}
