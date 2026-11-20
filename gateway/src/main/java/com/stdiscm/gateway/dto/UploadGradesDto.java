package com.stdiscm.gateway.dto;

import com.stdiscm.proto.grade.GradeInput;
import com.stdiscm.proto.grade.UploadGradesRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Grade values are validated by the grade service, not here. */
public record UploadGradesDto(
        @NotBlank String courseCode,
        @NotBlank String term,
        @NotEmpty List<@Valid Entry> grades) {

    public record Entry(@NotBlank String studentId, @NotNull Double grade) { }

    public UploadGradesRequest toProto() {
        UploadGradesRequest.Builder b = UploadGradesRequest.newBuilder()
                .setCourseCode(courseCode.trim())
                .setTerm(term.trim());
        for (Entry e : grades) {
            b.addGrades(GradeInput.newBuilder().setStudentId(e.studentId().trim()).setGrade(e.grade()));
        }
        return b.build();
    }
}
