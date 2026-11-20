# 内容模块数据库设计

[返回内容模块](design.md)

## 数据归属与字段

Flyway V2 创建 MySQL InnoDB 表 `content`，Flyway V4 加入发布字段并允许发布状态。内容模块拥有此表，作者外键指向[身份模块的账号表](../identity/database.md#数据归属与关系)。

| 字段 | 类型和约束 | 用途 |
| --- | --- | --- |
| `id` | `BIGINT NOT NULL AUTO_INCREMENT`，主键 | 内容标识，接口返回字符串 |
| `author_id` | `BIGINT NOT NULL`，引用 `user_account.id`，`ON DELETE RESTRICT` | 内容作者，从会话身份取得 |
| `status` | `VARCHAR(16) NOT NULL DEFAULT 'DRAFT'` | `DRAFT` 或 `PUBLISHED` |
| `title` | `VARCHAR(200) NOT NULL` | 去除首尾空白后的标题，允许空字符串 |
| `body_markdown` | `MEDIUMTEXT NOT NULL` | 原样保存正文，接口限制为 20,000 个 Unicode 码点 |
| `published_at` | `DATETIME(3) NULL` | 发布时由 MySQL 记录的首次发布时间，草稿必须为空 |
| `version` | `INT UNSIGNED NOT NULL DEFAULT 1` | 乐观锁版本，Java 用 `long` 表示 |
| `created_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 创建时间 |
| `updated_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 最后一次成功保存时间，修改时显式赋值 |

`chk_content_state_published_at` 强制 `DRAFT` 对应空 `published_at`，`PUBLISHED` 对应非空 `published_at`。其他状态与两种状态各自不匹配的发布时间均会被拒绝。

数据库连接使用 UTC 会话。在同一条插入语句中由数据库记录创建和修改时间。草稿修改更新 `updated_at`，保留 `created_at`。毫秒级排序相同时用内容 ID 排序。

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

从空库按 Flyway V1 至 V5 顺序应用迁移。草稿、发布和关注阶段都先确认[Canal 白名单](design.md#作者隔离与-cdc)。验收覆盖默认状态和版本、外键拒绝不存在的作者、毫秒精度、状态与发布时间约束和联合索引。

V4 到 V5 的增量验收使用保有草稿和已发布文章的数据库。复用已有数据库时，以 Flyway 已记录的版本及校验和为准。

## 发布事务与重试

发布与阅读使用同一条 `content` 行，沿用原 ID、作者和当前版本。Service 在短事务中按作者和 ID 锁定行，防止草稿保存与发布对同一旧版本同时成功。

```sql
SELECT id, author_id, status, title, body_markdown, version, published_at
FROM content
WHERE id = ? AND author_id = ?
FOR UPDATE;
```

不存在或不属于当前作者时返回 `404 DRAFT_NOT_FOUND`。若为 `DRAFT`，事务先检查请求版本和已保存的标题、正文，然后执行状态条件更新并记录首次发布时间、递增版本。

```sql
UPDATE content
SET status = 'PUBLISHED',
    published_at = CURRENT_TIMESTAMP(3),
    updated_at = CURRENT_TIMESTAMP(3),
    version = version + 1
WHERE id = ?
  AND author_id = ?
  AND status = 'DRAFT'
  AND version = ?;
```

草稿编辑若先提交，发布锁定行后读到新版本，旧版本请求返回 `409 CONTENT_VERSION_CONFLICT`。发布若先提交，等待中的草稿更新状态条件不再匹配，草稿 Service 返回 `404 DRAFT_NOT_FOUND`。

如果锁定的记录已是 `PUBLISHED`，仅当请求版本比记录当前版本小一时才作为同版本重试返回已有文章；响应沿用原版本和首次发布时间，不再写入。其他版本返回 `409 CONTENT_VERSION_CONFLICT`。此规则基于本阶段发布后不可编辑、下架或重新发布的约定。

文章详情按主键定位并检查 `status = 'PUBLISHED'`。Canal 白名单只含验证表，内容行更新不会产生 Kafka 事件。

## 已发布文章列表查询与索引

本人已发布列表读取现有 `content`，作者和状态为等值条件。Flyway V5 增加以下索引，草稿查询继续使用按 `updated_at` 排序的索引。

```sql
ALTER TABLE content
    ADD INDEX idx_content_author_status_published
    (author_id, status, published_at DESC, id DESC);
```

首页按作者和发布状态查询。续页在同一 SQL 中加入上次最后一项的发布时间和 ID 作为排他边界。

```sql
SELECT id, title, published_at
FROM content
WHERE author_id = ? AND status = 'PUBLISHED'
  AND (published_at < ? OR (published_at = ? AND id < ?))
ORDER BY published_at DESC, id DESC
LIMIT ?;
```

首页省略游标条件。每次读取 `limit + 1` 行，多出一行时使用最后一条实际返回的记录生成游标。翻页期间，排在当前游标之前的新文章可通过刷新首页读取。已有发布记录直接参与查询，无需数据回填。本地验收已验证 V4 增量升级。在包含 600 篇同毫秒发布文章的数据集上，`EXPLAIN` 显示已发布列表及草稿列表的首页和续页分别命中对应索引。
