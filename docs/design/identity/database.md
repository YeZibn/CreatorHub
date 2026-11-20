# 身份模块数据库设计

回到 [身份模块设计](design.md) · [API 设计](api.md)

## 数据归属与关系

`user_account` 拥有登录邮箱、密码哈希和账号状态。`user_profile` 拥有昵称等用户资料。登录查询可直接读取账号表，未来展示作者时可单独查询资料表。接口和 Service 仍负责授权及字段裁剪。

两张表使用同一个用户 ID。`user_account.id` 在插入账号时生成。`user_profile.user_id` 使用该值，同时做主键及外键。主键限制每个账号最多有一份资料，外键保证资料引用已存在的账号。注册事务保证正常注册完成后两行同时存在。

## MySQL 8.4 DDL

```sql
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
```

第一版使用 ASCII 邮箱。Java 去除首尾空白并统一小写，注册和登录使用同一个规范化方法。状态由注册 Service 显式写入 `ACTIVE`。`email_verified_at` 在本地演示阶段留空，`password_changed_at` 首次设置密码时写入。数据库会话使用 UTC，改昵称只更新资料行。

## 索引与查询

| 索引 | 用途 |
| --- | --- |
| `user_account.PRIMARY (id)` | 通过会话用户 ID 查询账号 |
| `uk_user_account_email (email_normalized)` | 注册时约束邮箱唯一，登录时等值查找账号 |
| `user_profile.PRIMARY (user_id)` | 按用户 ID 读取和更新资料，满足外键索引要求 |

登录使用 `WHERE email_normalized = ?`。账号唯一索引定位一行后再读取状态，因此无需邮箱与状态的联合索引。昵称允许重名，当前接口按用户 ID 访问资料。退出只操作 Redis 会话。

## 迁移和事务

首个身份迁移先建 `user_account`，再建 `user_profile`。实现时将迁移放在 `creatorhub-core/src/main/resources/db/migration/`，MyBatis 映射放在 `creatorhub-core/src/main/resources/mapper/identity/`。注册时从账号插入取得生成的 ID，继续插入资料。捕获 `uk_user_account_email` 的冲突并映射为邮箱重复响应，其他写入失败按事务错误处理。

验收时检查并发注册是否只留下一个账号及一份资料。用代表性数据和 `EXPLAIN` 核对登录、`/me` 和昵称更新的访问路径。
