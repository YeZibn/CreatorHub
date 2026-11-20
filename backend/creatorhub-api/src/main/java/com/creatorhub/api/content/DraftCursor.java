package com.creatorhub.api.content;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

record DraftCursor(LocalDateTime updatedAt, long id) {
    String encode() {
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli())
                .putLong(id)
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static DraftCursor decode(String value) {
        if (value.length() != 22) throw new IllegalArgumentException();
        var bytes = ByteBuffer.wrap(Base64.getUrlDecoder().decode(value));
        var time = LocalDateTime.ofInstant(Instant.ofEpochMilli(bytes.getLong()), ZoneOffset.UTC);
        var cursor = new DraftCursor(time, bytes.getLong());
        if (time.getYear() < 1000 || time.getYear() > 9999 || cursor.id() <= 0
                || !cursor.encode().equals(value)) {
            throw new IllegalArgumentException();
        }
        return cursor;
    }
}
