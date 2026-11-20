package com.creatorhub.api.content;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

record PublishedArticleCursor(LocalDateTime publishedAt, long id) {
    private static final byte TYPE = 1;
    private static final int ENCODED_LENGTH = 23;

    String encode() {
        byte[] bytes = ByteBuffer.allocate(1 + 2 * Long.BYTES)
                .put(TYPE)
                .putLong(publishedAt.toInstant(ZoneOffset.UTC).toEpochMilli())
                .putLong(id)
                .array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static PublishedArticleCursor decode(String value) {
        if (value.length() != ENCODED_LENGTH) throw new IllegalArgumentException();
        var bytes = ByteBuffer.wrap(Base64.getUrlDecoder().decode(value));
        if (bytes.get() != TYPE) throw new IllegalArgumentException();
        var time = LocalDateTime.ofInstant(Instant.ofEpochMilli(bytes.getLong()), ZoneOffset.UTC);
        var cursor = new PublishedArticleCursor(time, bytes.getLong());
        if (time.getYear() < 1000 || time.getYear() > 9999 || cursor.id() <= 0
                || !cursor.encode().equals(value)) {
            throw new IllegalArgumentException();
        }
        return cursor;
    }
}
