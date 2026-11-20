package com.stdiscm.gateway.controller;

import com.stdiscm.gateway.dto.AnnouncementDto;
import com.stdiscm.gateway.dto.PostAnnouncementDto;
import com.stdiscm.gateway.grpc.AnnouncementClient;
import com.stdiscm.gateway.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementClient announcements;
    private final SessionService sessions;

    public AnnouncementController(AnnouncementClient announcements, SessionService sessions) {
        this.announcements = announcements;
        this.sessions = sessions;
    }

    @GetMapping
    public List<AnnouncementDto> list(HttpServletRequest req) {
        String token = sessions.require(req).token();
        return announcements.list(token).stream().map(AnnouncementDto::from).toList();
    }

    @PostMapping
    public AnnouncementDto post(HttpServletRequest req, @Valid @RequestBody PostAnnouncementDto body) {
        String token = sessions.require(req).token();
        return AnnouncementDto.from(announcements.post(token, body.title(), body.body()));
    }
}
