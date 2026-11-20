CREATE TABLE content (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    author_id     BIGINT NOT NULL,
    status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    title         VARCHAR(200) NOT NULL,
    body_markdown MEDIUMTEXT NOT NULL,
    version       INT UNSIGNED NOT NULL DEFAULT 1,
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_content_author_status_updated (author_id, status, updated_at DESC, id DESC),
    CONSTRAINT fk_content_author FOREIGN KEY (author_id) REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_content_status CHECK (status = 'DRAFT')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
