package com.creatorhub.api;

import com.creatorhub.infrastructure.social.FollowCacheConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.creatorhub.api", "com.creatorhub.core"})
@Import(FollowCacheConfiguration.class)
@MapperScan({"com.creatorhub.core.identity.mapper", "com.creatorhub.core.content.mapper",
        "com.creatorhub.core.social.mapper"})
public class CreatorHubApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreatorHubApiApplication.class, args);
    }
}
