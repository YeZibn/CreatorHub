package com.creatorhub.api.content;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import com.creatorhub.api.identity.IdentityPrincipal;
import com.creatorhub.core.content.entity.PublishedArticle;
import com.creatorhub.core.content.service.DraftService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/content")
public class ArticleController {
    private static final Pattern DECIMAL_ID = Pattern.compile("[0-9]{1,19}");
    private final DraftService drafts;

    public ArticleController(DraftService drafts) {
        this.drafts = drafts;
    }

    @PostMapping(path = "/drafts/{id}/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PublishResponse> publish(@AuthenticationPrincipal IdentityPrincipal principal,
                                            @PathVariable String id, @RequestBody PublishRequest input) {
        long contentId = contentId(id);
        long version = version(input == null ? null : input.version());
        return publishResponse(drafts.publish(principal.userId(), contentId, version));
    }

    @GetMapping("/articles/{id}")
    ResponseEntity<ArticleResponse> article(@PathVariable String id) {
        return articleResponse(drafts.getPublished(contentId(id)));
    }

    private static ResponseEntity<ArticleResponse> articleResponse(PublishedArticle article) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ArticleResponse.from(article));
    }

    private static ResponseEntity<PublishResponse> publishResponse(PublishedArticle article) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PublishResponse.from(article));
    }

    private static long contentId(String value) {
        if (!DECIMAL_ID.matcher(value).matches()) throw new IllegalArgumentException();
        long id = Long.parseLong(value);
        if (id <= 0) throw new IllegalArgumentException();
        return id;
    }

    private static long version(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 1 || value.longValue() > 0xffff_ffffL) {
            throw new IllegalArgumentException();
        }
        return value.longValue();
    }

    record PublishRequest(JsonNode version) {
        @Override
        public String toString() {
            return "PublishRequest";
        }
    }

    record PublishResponse(String id, String authorId, String title, String body, String publishedAt, long version) {
        static PublishResponse from(PublishedArticle article) {
            return new PublishResponse(Long.toString(article.id()), Long.toString(article.authorId()),
                    article.title(), article.body(), utc(article.publishedAt()), article.version());
        }

        private static String utc(LocalDateTime value) {
            return value.toInstant(ZoneOffset.UTC).toString();
        }

        @Override
        public String toString() {
            return "PublishResponse[id=" + id + ", version=" + version + "]";
        }
    }

    record ArticleResponse(String id, String authorId, String title, String body, String publishedAt) {
        static ArticleResponse from(PublishedArticle article) {
            return new ArticleResponse(Long.toString(article.id()), Long.toString(article.authorId()),
                    article.title(), article.body(), utc(article.publishedAt()));
        }

        private static String utc(LocalDateTime value) {
            return value.toInstant(ZoneOffset.UTC).toString();
        }

        @Override
        public String toString() {
            return "ArticleResponse[id=" + id + "]";
        }
    }
}
