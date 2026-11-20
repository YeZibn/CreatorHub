package com.creatorhub.core.content.service;

import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.core.content.entity.Draft;
import com.creatorhub.core.content.entity.DraftSummary;
import com.creatorhub.core.content.entity.NewDraft;
import com.creatorhub.core.content.entity.PublishedArticle;
import com.creatorhub.core.content.entity.PublishedArticleSummary;
import com.creatorhub.core.content.entity.PublishableContent;
import com.creatorhub.core.content.mapper.DraftMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftService {
    private final DraftMapper drafts;

    public DraftService(DraftMapper drafts) {
        this.drafts = drafts;
    }

    @Transactional
    public Draft create(long authorId, String title, String body) {
        var draft = new NewDraft(authorId, title, body);
        drafts.insert(draft);
        return get(authorId, draft.getId());
    }

    public Draft get(long authorId, long id) {
        Draft draft = drafts.find(authorId, id);
        if (draft == null) throw new NotFound();
        return draft;
    }

    public List<DraftSummary> list(long authorId, int pageSize, LocalDateTime beforeTime, Long beforeId) {
        return drafts.list(authorId, pageSize, beforeTime, beforeId);
    }

    @Transactional
    public PublishedArticle publish(long authorId, long id, long requestedVersion) {
        PublishableContent content = drafts.findForUpdate(authorId, id);
        if (content == null) throw new NotFound();

        if ("PUBLISHED".equals(content.status())) {
            if (requestedVersion < 0xffff_ffffL && content.version() == requestedVersion + 1
                    && content.publishedAt() != null) {
                return publishedArticle(content);
            }
            throw new VersionConflict();
        }

        if (!"DRAFT".equals(content.status())) throw new NotFound();
        if (content.version() != requestedVersion) throw new VersionConflict();
        if (!containsNonWhitespace(content.title()) || !containsNonWhitespace(content.body())) {
            throw new InvalidForPublishing();
        }
        if (requestedVersion == 0xffff_ffffL || drafts.publish(authorId, id, requestedVersion) != 1) {
            throw new VersionConflict();
        }

        PublishedArticle article = drafts.findPublished(id);
        if (article == null) throw new IllegalStateException("Published content disappeared in its transaction");
        return article;
    }

    public PublishedArticle getPublished(long id) {
        PublishedArticle article = drafts.findPublished(id);
        if (article == null) throw new ArticleNotFound();
        return article;
    }

    public PublishedPage listPublished(long authorId, int limit, LocalDateTime beforeTime, Long beforeId) {
        List<PublishedArticleSummary> rows = drafts.listPublished(authorId, limit + 1, beforeTime, beforeId);
        boolean hasMore = rows.size() > limit;
        return new PublishedPage(hasMore ? rows.subList(0, limit) : rows, hasMore);
    }

    public record PublishedPage(List<PublishedArticleSummary> items, boolean hasMore) {}

    private static boolean containsNonWhitespace(String value) {
        return value.codePoints().anyMatch(
                codePoint -> !Character.isWhitespace(codePoint)
                        && !Character.isSpaceChar(codePoint) && codePoint != 0x85);
    }

    private static PublishedArticle publishedArticle(PublishableContent content) {
        return new PublishedArticle(content.id(), content.authorId(), content.title(), content.body(),
                content.version(), content.publishedAt());
    }

    @Transactional
    public Draft update(long authorId, long id, long version, String title, String body) {
        if (drafts.update(authorId, id, version, title, body) == 1) {
            return get(authorId, id);
        }
        // A locking read sees the current committed state under REPEATABLE READ.
        if (drafts.findVersionForShare(authorId, id) == null) throw new NotFound();
        throw new VersionConflict();
    }

    public static final class NotFound extends RuntimeException {}

    public static final class VersionConflict extends RuntimeException {}

    public static final class InvalidForPublishing extends RuntimeException {}

    public static final class ArticleNotFound extends RuntimeException {}
}
