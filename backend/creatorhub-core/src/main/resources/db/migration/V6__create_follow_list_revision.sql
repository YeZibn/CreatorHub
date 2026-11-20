CREATE TABLE follow_list_revision (
    owner_id BIGINT NOT NULL,
    direction TINYINT UNSIGNED NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (owner_id, direction),
    CONSTRAINT fk_follow_list_revision_owner
        FOREIGN KEY (owner_id) REFERENCES user_account (id) ON DELETE RESTRICT,
    CONSTRAINT chk_follow_list_revision_direction CHECK (direction IN (1, 2)),
    CONSTRAINT chk_follow_list_revision_positive CHECK (revision > 0)
) ENGINE = InnoDB;
