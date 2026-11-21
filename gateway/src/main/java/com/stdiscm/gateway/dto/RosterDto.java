package com.stdiscm.gateway.dto;

import com.stdiscm.proto.course.ListRosterResponse;

import java.util.List;

public record RosterDto(String courseCode, List<Student> students) {
    public record Student(String studentId, String studentName) { }

    public static RosterDto from(ListRosterResponse r) {
        return new RosterDto(r.getCourseCode(), r.getStudentsList().stream()
                .map(s -> new Student(s.getStudentId(), s.getStudentName()))
                .toList());
    }
}
