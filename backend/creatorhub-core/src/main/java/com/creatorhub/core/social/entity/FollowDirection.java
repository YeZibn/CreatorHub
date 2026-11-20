package com.creatorhub.core.social.entity;

public enum FollowDirection {
    FOLLOWING(1, "following"), FOLLOWERS(2, "followers");

    private final int code;
    private final String keySegment;

    FollowDirection(int code, String keySegment) {
        this.code = code;
        this.keySegment = keySegment;
    }

    public int code() {
        return code;
    }

    public String keySegment() {
        return keySegment;
    }
}
