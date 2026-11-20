package com.creatorhub.core.social.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.core.social.entity.FollowDisplayName;
import com.creatorhub.core.social.entity.FollowEntry;
import com.creatorhub.core.social.entity.FollowSummary;
import org.apache.ibatis.annotations.Param;

public interface FollowMapper {
    String targetStatusForShare(@Param("userId") long userId);

    void insert(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    int delete(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    long revision(@Param("ownerId") long ownerId, @Param("direction") int direction);

    void advanceRevision(@Param("ownerId") long ownerId, @Param("direction") int direction);

    List<FollowEntry> followingHead(@Param("ownerId") long ownerId, @Param("size") int size);

    List<FollowEntry> followersHead(@Param("ownerId") long ownerId, @Param("size") int size);

    List<FollowDisplayName> displayNames(@Param("ids") List<Long> ids);

    boolean exists(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    List<FollowSummary> listFollowing(@Param("userId") long userId, @Param("pageSize") int pageSize,
                                      @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId);

    List<FollowSummary> listFollowers(@Param("userId") long userId, @Param("pageSize") int pageSize,
                                      @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId);
}
