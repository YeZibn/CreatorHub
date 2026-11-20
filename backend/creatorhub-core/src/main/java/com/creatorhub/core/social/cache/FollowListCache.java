package com.creatorhub.core.social.cache;

import java.util.List;
import java.util.Optional;

import com.creatorhub.core.social.entity.FollowDirection;
import com.creatorhub.core.social.entity.FollowEntry;

public interface FollowListCache {
    int MAX_ENTRIES = 51;

    Optional<List<FollowEntry>> read(long ownerId, FollowDirection direction, long revision, int size);

    void populate(long ownerId, FollowDirection direction, long revision, List<FollowEntry> entries);

    final class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) {
            super(cause);
        }
    }
}
