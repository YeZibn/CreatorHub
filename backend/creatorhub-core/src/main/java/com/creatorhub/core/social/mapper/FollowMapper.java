package com.creatorhub.core.social.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.core.social.entity.FollowSummary;
import org.apache.ibatis.annotations.Param;

public interface FollowMapper {
    String targetStatusForShare(@Param("userId") long userId);

    void insert(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    void delete(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    boolean exists(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    List<FollowSummary> listFollowing(@Param("userId") long userId, @Param("pageSize") int pageSize,
                                      @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId);

    List<FollowSummary> listFollowers(@Param("userId") long userId, @Param("pageSize") int pageSize,
                                      @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId);
}
