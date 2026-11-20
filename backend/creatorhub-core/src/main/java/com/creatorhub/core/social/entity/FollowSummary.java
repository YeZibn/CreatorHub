package com.creatorhub.core.social.entity;

import java.time.LocalDateTime;

public record FollowSummary(long userId, String displayName, LocalDateTime followedAt) {}
