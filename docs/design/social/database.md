# 社交模块数据库设计

[返回社交模块](design.md) · [关系 API](api.md)

## 表与索引

Flyway [V3 迁移](../../../backend/creatorhub-core/src/main/resources/db/migration/V3__create_follow.sql)创建 InnoDB 表 `follow`。社交模块维护关系，身份模块维护[账号与资料](../identity/database.md#数据归属与关系)。

| 字段 | 类型与约束 | 用途 |
| --- | --- | --- |
| `follower_id` | `BIGINT NOT NULL`，外键引用 `user_account.id` | 发起关注的人 |
| `followee_id` | `BIGINT NOT NULL`，外键引用 `user_account.id` | 被关注的人 |
| `created_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 由 UTC 数据库会话记录此次关注的建立时间 |

两个外键均使用 `ON DELETE RESTRICT`。`CHECK (follower_id <> followee_id)` 禁止自关注。一行表示一条当前关系，取消时删除对应行，重新关注时记录新的建立时间。

| 索引 | 查询用途 |
| --- | --- |
| `PRIMARY KEY (follower_id, followee_id)` | 保证方向唯一，支持精确状态查询和取关 |
| `idx_follow_following (follower_id, created_at DESC, followee_id DESC)` | 按发起者分页查询关注 |
| `idx_follow_followers (followee_id, created_at DESC, follower_id DESC)` | 按目标作者分页查询粉丝 |

## 事务与并发

关注事务按目标账号主键执行 `SELECT status ... FOR SHARE`，确认状态为 `ACTIVE` 后写入关系。联合主键让并发插入竞争同一条关系。主键冲突视为幂等成功并保留原时间。

```sql
INSERT INTO follow (follower_id, followee_id)
VALUES (?, ?);
```

取关按主键两列删除，影响零行也表示目标关系已经不存在。两个写接口都在事务提交后返回 `204`。状态查询仅按联合主键检查关系是否存在。

## 游标查询

以粉丝续页为例，首个参数是当前会话用户 ID，游标提供随后两个时间参数和一个粉丝 ID，最后一个参数为 `limit + 1`。

```sql
SELECT f.follower_id AS user_id, p.display_name, f.created_at AS followed_at
FROM follow f
JOIN user_profile p ON p.user_id = f.follower_id
WHERE f.followee_id = ?
  AND (f.created_at < ? OR (f.created_at = ? AND f.follower_id < ?))
ORDER BY f.created_at DESC, f.follower_id DESC
LIMIT ?;
```

首页省略游标谓词。我的关注查询交换关系两端，同样以对端 ID 查昵称。额外一行用于判断是否还有下一页，游标使用最后一条实际返回记录。两份列表读取实时关系，翻页期间的新关注可能排在已翻过的位置，可刷新首页获取当前顺序。

## 列表版本表与迁移

Flyway [V6 迁移](../../../backend/creatorhub-core/src/main/resources/db/migration/V6__create_follow_list_revision.sql)新建社交模块的 `follow_list_revision`。表内每行只记录一个账号的一个列表方向当前是第几版。版本用于定位 Redis 中相应的首页快照。

| 字段 | 类型和约束 | 用途 |
| --- | --- | --- |
| `owner_id` | `BIGINT NOT NULL`，外键引用 `user_account.id`，`ON DELETE RESTRICT` | 列表属于哪个账号 |
| `direction` | `TINYINT UNSIGNED NOT NULL`，限制为 `1` 或 `2` | `1 = FOLLOWING`，`2 = FOLLOWERS` |
| `revision` | `BIGINT NOT NULL`，限制为正整数 | 当前列表版本 |

主键为 `(owner_id, direction)`，用于按账号及方向点查和更新。缺行在应用中解释为版本 `0`。V5 升级时仅建表，已有关系以版本 `0` 按需回填。首次实际变化插入版本 `1`，此后在 SQL 中原子加一。

```sql
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
```

### 关系变更与版本推进

1. 关注仍先检查目标为 `ACTIVE`。接入版本表后，使用普通 `INSERT` 并明确识别 `follow` 主键冲突为幂等成功，保留原 `created_at`。取关使用按主键删除的影响行数判断关系是否实际删除。
2. 新增或删除确实发生时，同一事务内分别推进 `(A, FOLLOWING)` 和 `(B, FOLLOWERS)`。把两个主键按 `(owner_id, direction)` 排序后依次执行下面的写入。重复关注或重复取关不推进版本。
3. 事务提交后返回 `204`。异常时关系与版本一起回滚。如对死锁做有限重试，从新的事务重跑整次关系操作。

```sql
INSERT INTO follow_list_revision (owner_id, direction, revision)
VALUES (?, ?, 1)
ON DUPLICATE KEY UPDATE revision = revision + 1;
```

### 缓存回源查询

查询版本时使用主库。缓存未命中时，在同一 MySQL 只读 `REPEATABLE READ` 事务内读取**版本和对应方向前 51 条关系**，两个查询都使用普通一致性读。关系查询沿用现有方向索引和排序条件，投影对端 ID 和 `created_at`。出事务后将它们写入该快照的版本 key。返回当前页前批量读取昵称并按原顺序拼装。若发现资料缺失，则使用现有带资料连接的 SQL 生成该页，保持列表行为一致。[缓存回填与异常处理](cache.md#首页读取与回填)
