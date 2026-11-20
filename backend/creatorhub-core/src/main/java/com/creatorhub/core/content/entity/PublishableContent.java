package com.creatorhub.core.content.entity;

import java.time.LocalDateTime;

public record PublishableContent(long id, long authorId, String status, String title, String body,
                                 long version, LocalDateTime publishedAt) {}
