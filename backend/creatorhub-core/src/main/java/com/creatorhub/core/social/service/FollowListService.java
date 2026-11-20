package com.creatorhub.core.social.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import com.creatorhub.core.social.cache.FollowListCache;
import com.creatorhub.core.social.entity.FollowDirection;
import com.creatorhub.core.social.entity.FollowEntry;
import com.creatorhub.core.social.entity.FollowSummary;
import com.creatorhub.core.social.mapper.FollowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FollowListService {
    private static final Logger LOG = LoggerFactory.getLogger(FollowListService.class);
    private final FollowMapper follows;
    private final FollowListCache cache;
    private final TransactionTemplate snapshotTransaction;

    public FollowListService(FollowMapper follows, ObjectProvider<FollowListCache> cache,
                             PlatformTransactionManager transactionManager) {
        this.follows = follows;
        this.cache = cache.getIfAvailable();
        this.snapshotTransaction = new TransactionTemplate(transactionManager);
        snapshotTransaction.setReadOnly(true);
        snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshotTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<FollowSummary> list(long ownerId, FollowDirection direction, int pageSize,
                                    LocalDateTime beforeTime, Long beforeId) {
        if (cache == null || beforeTime != null) {
            return listFromDatabase(ownerId, direction, pageSize, beforeTime, beforeId);
        }

        long revision = follows.revision(ownerId, direction.code());
        List<FollowEntry> entries;
        try {
            var cached = cache.read(ownerId, direction, revision, pageSize);
            LOG.debug("follow_list_cache_read direction={} result={}", direction, cached.isPresent() ? "hit" : "miss");
            entries = cached.orElse(null);
        } catch (FollowListCache.Unavailable failure) {
            LOG.debug("follow_list_cache_read direction={} result=fallback cause={}", direction, causeType(failure));
            return listFromDatabase(ownerId, direction, pageSize, null, null);
        }

        if (entries == null) {
            Snapshot snapshot = snapshotTransaction.execute(status -> new Snapshot(
                    follows.revision(ownerId, direction.code()), direction == FollowDirection.FOLLOWING
                        ? follows.followingHead(ownerId, FollowListCache.MAX_ENTRIES)
                        : follows.followersHead(ownerId, FollowListCache.MAX_ENTRIES)));
            LOG.debug("follow_list_cache_snapshot direction={}", direction);
            try {
                cache.populate(ownerId, direction, snapshot.revision(), snapshot.entries());
            } catch (FollowListCache.Unavailable failure) {
                LOG.debug("follow_list_cache_fill direction={} result=failed cause={}", direction, causeType(failure));
            }
            entries = snapshot.entries().subList(0, Math.min(pageSize, snapshot.entries().size()));
        }
        return summaries(ownerId, direction, pageSize, entries);
    }

    private List<FollowSummary> summaries(long ownerId, FollowDirection direction, int size, List<FollowEntry> entries) {
        if (entries.isEmpty()) return List.of();
        var names = new HashMap<Long, String>();
        follows.displayNames(entries.stream().map(FollowEntry::userId).toList())
                .forEach(row -> names.put(row.userId(), row.displayName()));
        if (names.size() != entries.size()) {
            LOG.debug("follow_list_cache_profiles direction={} result=fallback", direction);
            return listFromDatabase(ownerId, direction, size, null, null);
        }
        return entries.stream()
                .map(row -> new FollowSummary(row.userId(), names.get(row.userId()), row.followedAt()))
                .toList();
    }

    private List<FollowSummary> listFromDatabase(long ownerId, FollowDirection direction, int size,
                                                 LocalDateTime beforeTime, Long beforeId) {
        return direction == FollowDirection.FOLLOWING
                ? follows.listFollowing(ownerId, size, beforeTime, beforeId)
                : follows.listFollowers(ownerId, size, beforeTime, beforeId);
    }

    private static String causeType(FollowListCache.Unavailable failure) {
        return failure.getCause().getClass().getSimpleName();
    }

    private record Snapshot(long revision, List<FollowEntry> entries) {}
}
