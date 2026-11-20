CREATE TABLE user_account (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    email_normalized    VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash       VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email_verified_at   DATETIME(3) NULL,
    password_changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_email (email_normalized),
    CONSTRAINT chk_user_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE user_profile (
    user_id      BIGINT NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                             ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profile_account
        FOREIGN KEY (user_id) REFERENCES user_account (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
