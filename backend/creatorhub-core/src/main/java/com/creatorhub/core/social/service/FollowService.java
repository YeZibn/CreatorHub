package com.creatorhub.core.social.service;

import com.creatorhub.core.social.entity.FollowDirection;
import com.creatorhub.core.social.mapper.FollowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {
    private final FollowMapper follows;

    public FollowService(FollowMapper follows) {
        this.follows = follows;
    }

    @Transactional
    public void follow(long followerId, long followeeId) {
        if (!"ACTIVE".equals(follows.targetStatusForShare(followeeId))) {
            throw new TargetNotFound();
        }
        try {
            follows.insert(followerId, followeeId);
        } catch (DuplicateKeyException sameRelationship) {
            // The edge's composite primary key is the table's only unique constraint.
            return;
        }
        advanceRevisions(followerId, followeeId);
    }

    @Transactional
    public void unfollow(long followerId, long followeeId) {
        if (follows.delete(followerId, followeeId) == 1) {
            advanceRevisions(followerId, followeeId);
        }
    }

    private void advanceRevisions(long followerId, long followeeId) {
        if (followerId < followeeId) {
            follows.advanceRevision(followerId, FollowDirection.FOLLOWING.code());
            follows.advanceRevision(followeeId, FollowDirection.FOLLOWERS.code());
        } else {
            follows.advanceRevision(followeeId, FollowDirection.FOLLOWERS.code());
            follows.advanceRevision(followerId, FollowDirection.FOLLOWING.code());
        }
    }

    public boolean isFollowing(long followerId, long followeeId) {
        return follows.exists(followerId, followeeId);
    }

    public static final class TargetNotFound extends RuntimeException {}
}
