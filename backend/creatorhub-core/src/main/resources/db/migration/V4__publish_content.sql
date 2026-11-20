ALTER TABLE content
    ADD COLUMN published_at DATETIME(3) NULL AFTER body_markdown,
    DROP CHECK chk_content_status,
    ADD CONSTRAINT chk_content_state_published_at
        CHECK (
            (status = 'DRAFT' AND published_at IS NULL)
            OR
            (status = 'PUBLISHED' AND published_at IS NOT NULL)
        );
