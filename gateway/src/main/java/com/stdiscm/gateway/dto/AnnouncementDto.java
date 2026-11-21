package com.stdiscm.gateway.dto;

import com.stdiscm.proto.announcement.Announcement;

public record AnnouncementDto(String id, String title, String body, String authorName, long createdAt) {
    public static AnnouncementDto from(Announcement a) {
        return new AnnouncementDto(a.getId(), a.getTitle(), a.getBody(), a.getAuthorName(),
                a.getCreatedAt().getSeconds());
    }
}
