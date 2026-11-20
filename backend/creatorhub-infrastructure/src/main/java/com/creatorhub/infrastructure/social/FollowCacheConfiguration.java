package com.creatorhub.infrastructure.social;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "creatorhub.social.follow-cache.enabled", havingValue = "true")
public class FollowCacheConfiguration {
    @Bean(destroyMethod = "close")
    RedisFollowListCache followListCache(
            @Value("${creatorhub.social.follow-cache.host}") String host,
            @Value("${creatorhub.social.follow-cache.port}") int port,
            @Value("${creatorhub.social.follow-cache.timeout}") Duration timeout,
            @Value("${creatorhub.social.follow-cache.namespace}") String namespace) {
        return new RedisFollowListCache(host, port, timeout, namespace);
    }
}
