package com.stdiscm.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record PostAnnouncementDto(@NotBlank String title, @NotBlank String body) { }
