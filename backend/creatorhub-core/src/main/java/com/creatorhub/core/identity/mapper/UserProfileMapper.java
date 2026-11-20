package com.creatorhub.core.identity.mapper;

import com.creatorhub.core.identity.entity.UserProfile;
import org.apache.ibatis.annotations.Param;

public interface UserProfileMapper {

    UserProfile selectByUserId(@Param("userId") long userId);

    void insert(@Param("userId") long userId, @Param("displayName") String displayName);

    int updateDisplayName(@Param("userId") long userId, @Param("displayName") String displayName);
}
