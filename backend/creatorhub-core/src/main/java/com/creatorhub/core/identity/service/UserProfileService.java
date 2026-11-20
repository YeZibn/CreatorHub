package com.creatorhub.core.identity.service;

import com.creatorhub.core.identity.entity.UserProfile;
import com.creatorhub.core.identity.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private final UserProfileMapper profiles;

    public UserProfileService(UserProfileMapper profiles) {
        this.profiles = profiles;
    }

    public UserProfile get(long userId) {
        return profiles.selectByUserId(userId);
    }

    @Transactional
    public UserProfile changeDisplayName(long userId, String displayName) {
        profiles.updateDisplayName(userId, displayName);
        return profiles.selectByUserId(userId);
    }
}
