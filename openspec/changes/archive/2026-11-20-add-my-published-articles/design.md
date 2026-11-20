## Context

V4 已在 `content` 表记录发布状态与毫秒级发布时间。原有草稿列表索引按 `updated_at` 排序。文章详情沿用原内容 ID，对任何登录用户开放。工作台的接口分工已在[架构总纲](../../../../ARCHITECTURE.md#个人工作台)列出。

## Goals / Non-Goals

**Goals**

- 为当前登录作者提供分页摘要，列表结果与已提交的发布状态一致。
- 支持 V4 增量升级，旧文章可直接查询。首页和续页均能利用适配排序的索引。

**Non-Goals**

- 前端页面、他人主页、列表总数、Feed 分发和业务缓存。

## Decisions

1. **查询归内容模块。** 在 `creatorhub-api` 的内容包增加本人文章列表的 MVC 入口，沿用现有认证与错误响应。Controller 从 `IdentityPrincipal` 取作者 ID，显式校验查询参数名称、重复参数、条数和游标。内容 Service 调用 MyBatis Mapper，并将查询结果裁剪为当前页。身份模块继续提供会话。按请求查询 `content` 可以直接复用数据和事务，避免为工作台维护一套汇总表。
2. **同一 SQL 限制作者和状态。** MyBatis 查询按 `author_id = 会话用户 ID AND status = 'PUBLISHED'` 过滤，每页只投影 `id`、`title` 和 `published_at`。文章详情继续根据已发布状态授权，列表无需关注关系查询。[查询与索引设计](../../../../docs/design/content/database.md#已发布文章列表查询与索引)
3. **按两个字段做游标分页。** 新游标沿用无填充 Base64 URL 编码，载荷含独立类型标记、UTC 毫秒时间和正的 64 位 ID。校验标记、长度、规范编码和 MySQL 时间范围。解码值仅绑定为位置参数。续页使用 `published_at < ? OR (published_at = ? AND id < ?)`，按发布时间、ID 降序取 `limit + 1` 行。有下一页时用最后一条返回记录生成游标。同毫秒记录借助 ID 确定顺序，翻页无需按偏移跳过前面的记录。[接口设计](../../../../docs/design/content/api.md#本人已发布文章列表)
4. **按查询顺序建索引。** V5 增加 `idx_content_author_status_published (author_id, status, published_at DESC, id DESC)`。按发布时间读取需要专用索引，原草稿列表索引服务于按修改时间排序的查询。

## Risks / Trade-offs

- 实时列表翻页期间，新发布且排在当前游标前的文章在刷新首页后可见。游标不提供跨请求快照。
- 新增二级索引会占用存储并增加内容写入时的维护成本。以代表性数据对首页及续页执行 `EXPLAIN`，同时复查草稿列表计划。

## Migration Plan

从 V4 测试库应用 V5，验证原草稿、已发布文章及已有索引，随后开放列表接口。已有内容继续使用原行和发布时间，无需回填。若需要回退，先回退应用并核对 Flyway 对已应用迁移的校验行为。确需移除索引时，通过后续迁移执行并保留 V5 历史。
