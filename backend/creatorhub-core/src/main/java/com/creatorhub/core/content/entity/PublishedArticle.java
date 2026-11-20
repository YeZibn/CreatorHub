package com.creatorhub.core.content.entity;

import java.time.LocalDateTime;

public record PublishedArticle(long id, long authorId, String title, String body,
                               long version, LocalDateTime publishedAt) {
    @Override
    public String toString() {
        return "PublishedArticle[id=" + id + ", version=" + version + "]";
    }
}
