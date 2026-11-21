package com.stdiscm.gateway.dto;

import com.stdiscm.proto.grade.Grade;

public record GradeDto(String courseCode, String courseTitle, String term, double grade) {
    public static GradeDto from(Grade g) {
        return new GradeDto(g.getCourseCode(), g.getCourseTitle(), g.getTerm(), g.getGrade());
    }
}
