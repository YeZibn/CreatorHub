package com.creatorhub.core.content.entity;

import java.time.LocalDateTime;

public record Draft(long id, String title, String body, long version,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
    @Override
    public String toString() {
        return "Draft[id=" + id + ", version=" + version + "]";
    }
}
