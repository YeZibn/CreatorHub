package com.creatorhub.core.social.service;

import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.core.social.entity.FollowSummary;
import com.creatorhub.core.social.mapper.FollowMapper;
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
        follows.insert(followerId, followeeId);
    }

    @Transactional
    public void unfollow(long followerId, long followeeId) {
        follows.delete(followerId, followeeId);
    }

    public boolean isFollowing(long followerId, long followeeId) {
        return follows.exists(followerId, followeeId);
    }

    public List<FollowSummary> listFollowing(long userId, int pageSize, LocalDateTime beforeTime, Long beforeId) {
        return follows.listFollowing(userId, pageSize, beforeTime, beforeId);
    }

    public List<FollowSummary> listFollowers(long userId, int pageSize, LocalDateTime beforeTime, Long beforeId) {
        return follows.listFollowers(userId, pageSize, beforeTime, beforeId);
    }

    public static final class TargetNotFound extends RuntimeException {}
}
