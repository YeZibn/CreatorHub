package com.creatorhub.api.content;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import com.creatorhub.api.identity.IdentityPrincipal;
import com.creatorhub.core.content.entity.PublishedArticleSummary;
import com.creatorhub.core.content.service.DraftService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyArticlesController {
    private static final Set<String> PARAMETERS = Set.of("limit", "cursor");
    private final DraftService content;

    public MyArticlesController(DraftService content) {
        this.content = content;
    }

    @GetMapping("/api/v1/me/articles")
    ResponseEntity<Page> list(@AuthenticationPrincipal IdentityPrincipal principal,
                              @RequestParam MultiValueMap<String, String> parameters) {
        for (var parameter : parameters.entrySet()) {
            if (!PARAMETERS.contains(parameter.getKey()) || parameter.getValue().size() != 1) {
                throw new IllegalArgumentException();
            }
        }

        int limit = pageLimit(parameters.getFirst("limit"));
        String cursor = parameters.getFirst("cursor");
        PublishedArticleCursor after = cursor == null ? null : PublishedArticleCursor.decode(cursor);
        var page = content.listPublished(principal.userId(), limit,
                after == null ? null : after.publishedAt(), after == null ? null : after.id());
        String next = null;
        if (page.hasMore()) {
            var last = page.items().getLast();
            next = new PublishedArticleCursor(last.publishedAt(), last.id()).encode();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Page(page.items().stream().map(Summary::from).toList(), next));
    }

    private static int pageLimit(String value) {
        if (value == null) return 20;
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
        int limit = Integer.parseInt(value);
        if (limit < 1 || limit > 50) throw new IllegalArgumentException();
        return limit;
    }

    record Summary(String id, String title, String publishedAt) {
        static Summary from(PublishedArticleSummary row) {
            return new Summary(Long.toString(row.id()), row.title(),
                    row.publishedAt().toInstant(ZoneOffset.UTC).toString());
        }
    }

    record Page(List<Summary> items, String nextCursor) {}
}
