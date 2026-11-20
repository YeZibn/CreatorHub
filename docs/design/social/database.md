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

关注事务按目标账号主键执行 `SELECT status ... FOR SHARE`，确认状态为 `ACTIVE` 后写入关系。联合主键让并发插入竞争同一条关系。重复键分支保留已有时间。

```sql
INSERT INTO follow (follower_id, followee_id)
VALUES (?, ?)
ON DUPLICATE KEY UPDATE created_at = created_at;
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
