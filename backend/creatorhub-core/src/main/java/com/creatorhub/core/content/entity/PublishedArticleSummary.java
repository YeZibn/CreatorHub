package com.creatorhub.core.content.entity;

import java.time.LocalDateTime;

public record PublishedArticleSummary(long id, String title, LocalDateTime publishedAt) {}
