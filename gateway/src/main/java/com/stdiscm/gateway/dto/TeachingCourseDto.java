package com.stdiscm.gateway.dto;

import com.stdiscm.proto.grade.TeachingCourse;

public record TeachingCourseDto(String courseCode, String courseTitle) {
    public static TeachingCourseDto from(TeachingCourse c) {
        return new TeachingCourseDto(c.getCourseCode(), c.getCourseTitle());
    }
}
