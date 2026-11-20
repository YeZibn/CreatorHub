package com.creatorhub.core.content.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.core.content.entity.Draft;
import com.creatorhub.core.content.entity.DraftSummary;
import com.creatorhub.core.content.entity.NewDraft;
import org.apache.ibatis.annotations.Param;

public interface DraftMapper {
    void insert(NewDraft draft);

    Draft find(@Param("authorId") long authorId, @Param("id") long id);

    List<DraftSummary> list(@Param("authorId") long authorId, @Param("pageSize") int pageSize,
                            @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId);

    int update(@Param("authorId") long authorId, @Param("id") long id, @Param("version") long version,
               @Param("title") String title, @Param("body") String body);

    Long findVersionForShare(@Param("authorId") long authorId, @Param("id") long id);
}
