package com.stdiscm.gateway.controller;

import com.stdiscm.gateway.dto.CourseDto;
import com.stdiscm.gateway.dto.RosterDto;
import com.stdiscm.gateway.grpc.CourseClient;
import com.stdiscm.gateway.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Thin controller: calls the course node and returns the result. Rules live in the service. */
@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseClient courses;
    private final SessionService sessions;

    public CourseController(CourseClient courses, SessionService sessions) {
        this.courses = courses;
        this.sessions = sessions;
    }

    @GetMapping("/courses")
    public List<CourseDto> list(HttpServletRequest req) {
        String token = sessions.require(req).token();
        return courses.listCourses(token).stream().map(CourseDto::from).toList();
    }

    @PostMapping("/courses/{code}/enroll")
    public CourseDto enroll(HttpServletRequest req, @PathVariable String code) {
        String token = sessions.require(req).token();
        return CourseDto.from(courses.enroll(token, code));
    }

    @GetMapping("/enrollments")
    public List<CourseDto> myEnrollments(HttpServletRequest req) {
        String token = sessions.require(req).token();
        return courses.listMyEnrollments(token).stream().map(CourseDto::from).toList();
    }

    @GetMapping("/courses/{code}/roster")
    public RosterDto roster(HttpServletRequest req, @PathVariable String code) {
        String token = sessions.require(req).token();
        return RosterDto.from(courses.listRoster(token, code));
    }
}
