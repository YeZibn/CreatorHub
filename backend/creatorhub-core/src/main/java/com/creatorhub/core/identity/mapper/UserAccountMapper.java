package com.creatorhub.core.identity.mapper;

import com.creatorhub.core.identity.entity.NewAccount;
import com.creatorhub.core.identity.entity.UserAccount;
import org.apache.ibatis.annotations.Param;

public interface UserAccountMapper {

    UserAccount selectByEmail(@Param("email") String email);

    String selectEmailById(@Param("userId") long userId);

    void insert(NewAccount account);
}
