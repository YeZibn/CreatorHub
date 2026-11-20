# 内容模块数据库设计

[返回内容模块](design.md)

## 数据归属与字段

Flyway V2 创建 MySQL InnoDB 表 `content`，字符集为 `utf8mb4`。内容模块拥有此表，作者外键指向[身份模块的账号表](../identity/database.md#数据归属与关系)。

| 字段 | 类型和约束 | 用途 |
| --- | --- | --- |
| `id` | `BIGINT NOT NULL AUTO_INCREMENT`，主键 | 内容标识，接口返回字符串 |
| `author_id` | `BIGINT NOT NULL`，引用 `user_account.id`，`ON DELETE RESTRICT` | 内容作者，从会话身份取得 |
| `status` | `VARCHAR(16) NOT NULL DEFAULT 'DRAFT'`，`CHECK (status = 'DRAFT')` | 当前阶段的持久化状态 |
| `title` | `VARCHAR(200) NOT NULL` | 去除首尾空白后的标题，允许空字符串 |
| `body_markdown` | `MEDIUMTEXT NOT NULL` | 原样保存正文，接口限制为 20,000 个 Unicode 码点 |
| `version` | `INT UNSIGNED NOT NULL DEFAULT 1` | 乐观锁版本，Java 用 `long` 表示 |
| `created_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 创建时间 |
| `updated_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 最后一次成功保存时间，修改时显式赋值 |

数据库连接使用 UTC 会话。在同一条插入语句中由数据库记录创建和修改时间。修改只更新 `updated_at`，毫秒级排序相同时用内容 ID 排序。

## 索引与分页

详情和修改按主键定位，再在同一 SQL 检查作者与状态。`idx_content_author_status_updated (author_id, status, updated_at DESC, id DESC)` 支持作者草稿列表，首列也满足作者外键的索引要求。

```sql
SELECT id, title, updated_at
FROM content
WHERE author_id = ? AND status = 'DRAFT'
  AND (updated_at < ? OR (updated_at = ? AND id < ?))
ORDER BY updated_at DESC, id DESC
LIMIT ?;
```

首页省略游标条件。后续页绑定上一页最后一项的 UTC 毫秒时间和 ID。查询取 `limit + 1` 行，额外一行只用于判断是否还有下一页，游标以最后一条实际返回记录生成。列表是实时视图，编辑可能把草稿移动到已翻过的位置，刷新首页可看到最新顺序。

## 保存事务与版本竞争

创建时在同一事务内插入草稿、获取生成的 ID 并读取详情，提交后返回。修改时先执行以下条件更新。

```sql
UPDATE content
SET title = ?,
    body_markdown = ?,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = ?
  AND author_id = ?
  AND status = 'DRAFT'
  AND version = ?;
```

- 影响一行时，在同一事务内按归属读取详情，提交后返回新的版本。
- 影响零行时，用带相同 ID、作者和状态条件的 `SELECT version ... FOR SHARE` 做当前读。无匹配行返回 `404`。存在匹配行则表明版本已变，返回 `409`，响应省略当前版本和正文。
- 两个事务同时提交同一个版本时，第一笔成功提交后版本已经递增。另一笔重新匹配条件时影响零行，无法覆盖已提交的修改。

## 迁移顺序与本地数据

先按[内容模块的 CDC 要求](design.md#作者隔离与-cdc)应用并确认白名单，再在已完成身份迁移的数据库上验证 V2。验收覆盖默认状态和版本、外键拒绝不存在的作者、毫秒精度和联合索引。

用空库验证完整迁移链，或在已停在 V1 的测试库中验证 V2 增量迁移。复用已有数据库时，以 Flyway 已记录的版本及校验和为准。
