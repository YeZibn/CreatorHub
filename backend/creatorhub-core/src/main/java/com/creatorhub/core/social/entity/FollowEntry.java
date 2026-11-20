package com.creatorhub.core.social.entity;

import java.time.LocalDateTime;

public record FollowEntry(long userId, LocalDateTime followedAt) {}
