## Context

[架构总纲](../../../../ARCHITECTURE.md)已确定身份模块先行。Spring Security 可从 Redis 会话建立用户身份，Vue 可复用当前登录状态和 CSRF 请求逻辑。[现有 Compose](../../../../compose.yaml)的 Canal 白名单包含 `content`，MySQL 使用完整行镜像，因此需要先调整采集范围，再允许保存草稿。

## Goals / Non-Goals

**Goals**

- 在一条内容记录上保存创作进度，使未来的发布可以沿用同一个内容 ID。
- 在查询和更新时强制作者隔离，明确并发保存及分页行为。
- 保持已验证的 CDC 链路，同时让私有草稿留在业务存储中。

**Non-Goals**

- 本次仅支持 `DRAFT`。发布状态、发布时间和分发事件在发布变更中决定。
- 复用已有会话与权限机制。草稿不经过公开阅读端点或 Feed。

## Decisions

### 存储与索引

新增 Flyway V2 迁移，创建 MySQL InnoDB `content` 表，字符集为 `utf8mb4`。

| 字段 | 类型与约束 |
| --- | --- |
| `id` | `BIGINT` 自增主键，与既有用户 ID 对齐，API 序列化为字符串 |
| `author_id` | `BIGINT NOT NULL`，外键引用 `user_account.id`，删除策略为 `RESTRICT` |
| `status` | `VARCHAR(16) NOT NULL`，本阶段只允许 `DRAFT` |
| `title` | `VARCHAR(200) NOT NULL` |
| `body_markdown` | `MEDIUMTEXT NOT NULL`，容纳正文的多字节字符 |
| `version` | `INT UNSIGNED NOT NULL DEFAULT 1`，API 用整数，Java 可用 `long` 表示 |
| `created_at`、`updated_at` | `DATETIME(3) NOT NULL`，使用同一 MySQL UTC 会话，插入时记录当前时间 |

主键定位单条草稿。`idx_content_author_status_updated (author_id, status, updated_at DESC, id DESC)` 服务个人草稿列表，其首列也满足作者外键的索引需求。详情通过主键定位后在同一 SQL 检查作者和状态。列表只查摘要字段，通过 `limit + 1` 判断下一页，游标采用最后一条返回记录的毫秒时间戳和 ID，编码为 URL 安全的字符串。

下一页在作者和状态谓词之外增加 `updated_at < ? OR (updated_at = ? AND id < ?)`。每一页始终使用当前会话中的作者 ID。列表是实时视图，编辑可能把某条草稿移到游标之前，刷新首页即可看到新排序。

### 鉴权、写入与并发

`creatorhub-api` 处理会话身份、请求与错误响应，`creatorhub-core` 的内容 Service 和 MyBatis Mapper 执行事务与 SQL。写入从现有认证上下文获取操作者。DTO 只接收增量规格中的字段，校验正文长度时使用 Unicode 码点数，同时保留正文原样。

修改使用单条条件更新。数据校验和事务划分以[增量规格](specs/content-drafts/spec.md)为准。

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

更新成功后，在同一事务读取响应所需的草稿数据，提交后返回。影响行数为零时，用带相同作者及状态约束的当前读区分本人的过期版本与不可访问的草稿。数据库条件更新承担单行竞争，避免扩大锁的范围。

### 编辑器

Vue 提供「我的草稿」入口、分页列表和纯文本 Markdown 编辑器。新建编辑器首次点击保存时才发出 POST，已有草稿保存时携带打开或上一次保存得到的版本。保存期间禁用重复提交。收到冲突时保留本地内容，让用户主动重新加载；请求超时后也保留输入，避免自动重复创建或用旧版本覆盖。

### CDC

在 Canal 白名单中移除 `content`，保留其余表配置。保持 MySQL 的现有 ROW/FULL 配置，只改变向 Kafka 投递的范围。发布开始设计时再确定事件字段与可见性过滤。

## Risks / Trade-offs

- 草稿 ID 可被猜测 → 查询和更新在 SQL 中同时检查作者及草稿状态。对无权访问的资源返回统一 `404`。
- 旧页面覆盖新稿 → 比较版本后原子更新，前端在冲突时保留未保存输入。
- 仅检查 Kafka 中没有消息会误把 CDC 停机当成保护成功 → 记录消费位点，在草稿创建及更新后向验证表写入独立标记，等待收到该标记并检查同一分区中此前没有草稿事件或测试正文。

## Migration Plan

先应用 Canal 白名单并确认实例生效，再启动执行 V2 的 API。运行端到端验收，检查 MySQL 索引、双账号隔离及 CDC 正反对照后，再把草稿入口交给用户。需要回退时可以先回退应用，保留草稿表与排除 `content` 的 CDC 配置。
