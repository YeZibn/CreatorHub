package com.creatorhub.core.content.entity;

import java.time.LocalDateTime;

public record DraftSummary(long id, String title, LocalDateTime updatedAt) {}
