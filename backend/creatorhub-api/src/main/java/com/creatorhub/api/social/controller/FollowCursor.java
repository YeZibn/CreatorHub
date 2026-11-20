package com.creatorhub.api.social.controller;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

record FollowCursor(LocalDateTime followedAt, long userId) {
    enum Direction {
        FOLLOWING((byte) 1), FOLLOWERS((byte) 2);

        private final byte code;

        Direction(byte code) {
            this.code = code;
        }
    }

    String encode(Direction direction) {
        byte[] bytes = ByteBuffer.allocate(17)
                .put(direction.code)
                .putLong(followedAt.toInstant(ZoneOffset.UTC).toEpochMilli())
                .putLong(userId)
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static FollowCursor decode(String value, Direction direction) {
        if (value.length() != 23) throw new IllegalArgumentException();
        var bytes = ByteBuffer.wrap(Base64.getUrlDecoder().decode(value));
        if (bytes.get() != direction.code) throw new IllegalArgumentException();
        var time = LocalDateTime.ofInstant(Instant.ofEpochMilli(bytes.getLong()), ZoneOffset.UTC);
        var cursor = new FollowCursor(time, bytes.getLong());
        if (time.getYear() < 1000 || time.getYear() > 9999 || cursor.userId() <= 0
                || !cursor.encode(direction).equals(value)) {
            throw new IllegalArgumentException();
        }
        return cursor;
    }
}
