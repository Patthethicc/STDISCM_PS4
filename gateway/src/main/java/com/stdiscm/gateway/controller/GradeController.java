package com.stdiscm.gateway.controller;

import com.stdiscm.gateway.dto.GradeDto;
import com.stdiscm.gateway.dto.TeachingCourseDto;
import com.stdiscm.gateway.dto.UploadGradesDto;
import com.stdiscm.gateway.grpc.GradeClient;
import com.stdiscm.gateway.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Thin controller: calls the grade node and returns the result. Rules live in the service. */
@RestController
@RequestMapping("/api/grades")
public class GradeController {

    private final GradeClient grades;
    private final SessionService sessions;

    public GradeController(GradeClient grades, SessionService sessions) {
        this.grades = grades;
        this.sessions = sessions;
    }

    @GetMapping
    public List<GradeDto> myGrades(HttpServletRequest req) {
        String token = sessions.require(req).token();
        return grades.getMyGrades(token).stream().map(GradeDto::from).toList();
    }

    @GetMapping("/teaching")
    public List<TeachingCourseDto> teaching(HttpServletRequest req) {
        String token = sessions.require(req).token();
        return grades.listMyTeachingCourses(token).stream().map(TeachingCourseDto::from).toList();
    }

    @PostMapping
    public Map<String, Integer> upload(HttpServletRequest req, @Valid @RequestBody UploadGradesDto body) {
        String token = sessions.require(req).token();
        return Map.of("saved", grades.uploadGrades(token, body.toProto()));
    }
}
