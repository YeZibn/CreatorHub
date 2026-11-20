package com.creatorhub.infrastructure.social;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.creatorhub.core.social.cache.FollowListCache;
import com.creatorhub.core.social.entity.FollowDirection;
import com.creatorhub.core.social.entity.FollowEntry;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisFollowListCache implements FollowListCache, AutoCloseable {
    private static final String EMPTY = "__empty__";
    private static final DefaultRedisScript<Long> POPULATE = new DefaultRedisScript<>();

    static {
        POPULATE.setLocation(new ClassPathResource("redis/social/populate-follow-head.lua"));
        POPULATE.setResultType(Long.class);
    }

    private final String keyPrefix;
    private final LettuceConnectionFactory factory;
    private final StringRedisTemplate redis;

    RedisFollowListCache(String host, int port, Duration timeout, String namespace) {
        if (!namespace.matches("[a-zA-Z0-9_-]{1,32}") || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Invalid follow cache namespace or timeout");
        }
        keyPrefix = "creatorhub:social:follow:" + namespace + ":";
        var options = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build();
        var client = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .clientOptions(options)
                .build();
        // Keep the business timeout and connection independent of Spring Session.
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), client);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
    }

    @Override
    public Optional<List<FollowEntry>> read(long ownerId, FollowDirection direction, long revision, int size) {
        try {
            var rows = redis.opsForZSet().reverseRangeWithScores(key(ownerId, direction, revision), 0, size - 1);
            if (rows == null || rows.isEmpty()) return Optional.empty();
            var result = new ArrayList<FollowEntry>(rows.size());
            var ids = new HashSet<Long>();
            for (var row : rows) {
                String member = row.getValue();
                Double score = row.getScore();
                if (EMPTY.equals(member) && rows.size() == 1 && Double.valueOf(0).equals(score)) {
                    return Optional.of(List.of());
                }
                if (member == null || !member.matches("[0-9]{19}") || score == null
                        || !Double.isFinite(score) || score != Math.rint(score)) {
                    throw new IllegalArgumentException("Invalid follow cache entry");
                }
                long id = Long.parseLong(member);
                var time = LocalDateTime.ofInstant(Instant.ofEpochMilli(score.longValue()), ZoneOffset.UTC);
                if (id <= 0 || !ids.add(id) || time.getYear() < 1000 || time.getYear() > 9999) {
                    throw new IllegalArgumentException("Invalid follow cache entry");
                }
                result.add(new FollowEntry(id, time));
            }
            return Optional.of(result);
        } catch (DataAccessException | IllegalArgumentException failure) {
            throw new Unavailable(failure);
        }
    }

    @Override
    public void populate(long ownerId, FollowDirection direction, long revision, List<FollowEntry> entries) {
        var args = new ArrayList<String>(1 + 2 * entries.size());
        args.add(Long.toString(60_000 + ThreadLocalRandom.current().nextLong(30_001)));
        if (entries.isEmpty()) {
            args.add("0");
            args.add(EMPTY);
        } else {
            for (FollowEntry entry : entries) {
                args.add(Long.toString(entry.followedAt().toInstant(ZoneOffset.UTC).toEpochMilli()));
                args.add(String.format(Locale.ROOT, "%019d", entry.userId()));
            }
        }
        try {
            redis.execute(POPULATE, List.of(key(ownerId, direction, revision)), args.toArray());
        } catch (DataAccessException failure) {
            throw new Unavailable(failure);
        }
    }

    private String key(long ownerId, FollowDirection direction, long revision) {
        return keyPrefix + ownerId + ":" + direction.keySegment() + ":" + revision;
    }

    @Override
    public void close() {
        factory.destroy();
    }
}
