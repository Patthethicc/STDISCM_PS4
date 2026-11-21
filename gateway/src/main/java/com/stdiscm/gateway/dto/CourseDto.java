package com.stdiscm.gateway.dto;

import com.stdiscm.proto.course.Course;

public record CourseDto(String code, String title, int capacity, int enrolled, boolean open) {
    public static CourseDto from(Course c) {
        return new CourseDto(c.getCode(), c.getTitle(), c.getCapacity(), c.getEnrolled(), c.getOpen());
    }
}
