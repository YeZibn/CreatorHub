package com.creatorhub.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.creatorhub.api", "com.creatorhub.core"})
@MapperScan("com.creatorhub.core.identity.mapper")
public class CreatorHubApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreatorHubApiApplication.class, args);
    }
}
