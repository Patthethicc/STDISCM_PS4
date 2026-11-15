package com.stdiscm.feature.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcements")
public class AnnouncementEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnnouncementEntity() { }

    public AnnouncementEntity(String title, String body, UUID authorId, String authorName) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.body = body;
        this.authorId = authorId;
        this.authorName = authorName;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public UUID getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public Instant getCreatedAt() { return createdAt; }
}
