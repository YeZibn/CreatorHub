CREATE TABLE follow (
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (follower_id, followee_id),
    KEY idx_follow_following (follower_id, created_at DESC, followee_id DESC),
    KEY idx_follow_followers (followee_id, created_at DESC, follower_id DESC),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES user_account (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_follow_distinct_users CHECK (follower_id <> followee_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
