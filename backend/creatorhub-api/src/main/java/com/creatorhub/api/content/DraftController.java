package com.creatorhub.api.content;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.IntPredicate;

import com.creatorhub.api.identity.IdentityPrincipal;
import com.creatorhub.core.content.entity.Draft;
import com.creatorhub.core.content.entity.DraftSummary;
import com.creatorhub.core.content.service.DraftService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/content/drafts")
public class DraftController {
    private static final IntPredicate NON_WHITESPACE =
            c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c) && c != 0x85;
    private final DraftService drafts;

    public DraftController(DraftService drafts) {
        this.drafts = drafts;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Detail> create(@AuthenticationPrincipal IdentityPrincipal principal, @RequestBody CreateDraft input) {
        String title = validateText(input.title(), input.body());
        Draft draft = drafts.create(principal.userId(), title, input.body());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(Detail.from(draft));
    }

    @GetMapping("/{id}")
    ResponseEntity<Detail> get(@AuthenticationPrincipal IdentityPrincipal principal, @PathVariable long id) {
        return noStore(Detail.from(drafts.get(principal.userId(), id)));
    }

    @GetMapping
    ResponseEntity<Page> list(@AuthenticationPrincipal IdentityPrincipal principal,
                              @RequestParam(required = false) String limit,
                              @RequestParam(required = false) String cursor) {
        int size = limit == null ? 20 : Integer.parseInt(limit);
        if (size < 1 || size > 50) throw new IllegalArgumentException();
        DraftCursor after = cursor == null ? null : DraftCursor.decode(cursor);
        List<DraftSummary> rows = drafts.list(principal.userId(), size + 1,
                after == null ? null : after.updatedAt(), after == null ? null : after.id());
        List<DraftSummary> page = rows.subList(0, Math.min(size, rows.size()));
        String next = rows.size() > size
                ? new DraftCursor(page.getLast().updatedAt(), page.getLast().id()).encode() : null;
        return noStore(new Page(page.stream().map(Summary::from).toList(), next));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Detail> update(@AuthenticationPrincipal IdentityPrincipal principal,
                                  @PathVariable long id, @RequestBody UpdateDraft input) {
        String title = validateText(input.title(), input.body());
        JsonNode value = input.version();
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 1 || value.longValue() > 0xffff_ffffL) {
            throw new IllegalArgumentException();
        }
        return noStore(Detail.from(drafts.update(principal.userId(), id, value.longValue(), title, input.body())));
    }

    private static String validateText(String title, String body) {
        if (title == null || body == null) throw new IllegalArgumentException();
        int start = 0;
        int end = title.length();
        while (start < end && !NON_WHITESPACE.test(title.codePointAt(start))) {
            start += Character.charCount(title.codePointAt(start));
        }
        while (end > start && !NON_WHITESPACE.test(title.codePointBefore(end))) {
            end -= Character.charCount(title.codePointBefore(end));
        }
        String normalized = title.substring(start, end);
        if (normalized.codePointCount(0, normalized.length()) > 200
                || body.codePointCount(0, body.length()) > 20_000
                || (normalized.isEmpty() && body.codePoints().noneMatch(NON_WHITESPACE))
                || normalized.codePoints().anyMatch(DraftController::isSurrogate)
                || body.codePoints().anyMatch(DraftController::isSurrogate)) {
            throw new IllegalArgumentException();
        }
        return normalized;
    }

    private static boolean isSurrogate(int codePoint) {
        return codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE;
    }

    private static <T> ResponseEntity<T> noStore(T response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    private static String utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }

    record CreateDraft(String title, String body) {
        @Override
        public String toString() {
            return "CreateDraft";
        }
    }

    record UpdateDraft(String title, String body, JsonNode version) {
        @Override
        public String toString() {
            return "UpdateDraft";
        }
    }

    record Detail(String id, String title, String body, long version, String createdAt, String updatedAt) {
        static Detail from(Draft draft) {
            return new Detail(Long.toString(draft.id()), draft.title(), draft.body(), draft.version(),
                    utc(draft.createdAt()), utc(draft.updatedAt()));
        }

        @Override
        public String toString() {
            return "Detail[id=" + id + ", version=" + version + "]";
        }
    }

    record Summary(String id, String title, String updatedAt) {
        static Summary from(DraftSummary draft) {
            return new Summary(Long.toString(draft.id()), draft.title(), utc(draft.updatedAt()));
        }
    }

    record Page(List<Summary> items, String nextCursor) {}
}
