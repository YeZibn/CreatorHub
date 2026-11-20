## Why

登录后的创作者需要保存和继续编辑自己的文章草稿。先建立私有草稿和并发保存规则，后续发布与 Feed 可以沿用同一个内容 ID 和清楚的可见性边界。

## What Changes

- 新增手动创建、查看和修改草稿，以及按最近修改时间分页查看本人的草稿。
- 草稿只对作者可见。修改时校验版本，旧页面不能覆盖已保存的新内容。
- Vue 提供「我的草稿」和编辑器。保存发生版本冲突时保留本地输入，让作者决定是否重新加载。
- 当前阶段停止订阅 `content` 表的 CDC，避免私有草稿进入 Kafka，保留既有 CDC 验证链路。
- 发布、下架、删除、自动保存和公开阅读留给后续变更。

## Capabilities

### New Capabilities

- `content-drafts` 覆盖作者私有草稿、列表分页以及并发编辑行为。

### Modified Capabilities

无。

## Impact

- API 新增 `/api/v1/content/drafts` 及详情接口，复用[用户身份规格](../../../../openspec/specs/user-identity/spec.md)中的会话与 CSRF 约定。
- MySQL 新增 `content` 表和作者草稿列表索引。Spring MVC 和 MyBatis 各增加内容模块代码，Vue 增加草稿入口和编辑页面。
- 更新 [Compose](../../../../compose.yaml) 中的 Canal 表白名单。在草稿写入启用前部署新的白名单。
