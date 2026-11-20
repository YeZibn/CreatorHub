ALTER TABLE content
    ADD INDEX idx_content_author_status_published (author_id, status, published_at DESC, id DESC);
