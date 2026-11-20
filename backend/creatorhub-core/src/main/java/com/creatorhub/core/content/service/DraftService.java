package com.creatorhub.core.content.service;

import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.core.content.entity.Draft;
import com.creatorhub.core.content.entity.DraftSummary;
import com.creatorhub.core.content.entity.NewDraft;
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
}
