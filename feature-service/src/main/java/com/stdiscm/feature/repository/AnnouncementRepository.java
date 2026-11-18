package com.stdiscm.feature.repository;

import com.stdiscm.feature.model.AnnouncementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, UUID> {
    List<AnnouncementEntity> findAllByOrderByCreatedAtDesc();
}
