# CreatorHub 架构总纲

CreatorHub 是面向创作者的内容社区。用户可以写文章、关注作者并浏览个人 Feed。Java 负责账号、业务数据和内容分发。后续 Python Agent 通过 Java API 检索内容和保存草稿，由用户确认发布。

## 运行边界

Java 后端计划采用 Spring MVC、原生 MyBatis 和四个 Maven 子模块。`creatorhub-api` 提供 HTTP 接口，`creatorhub-consumer` 订阅 Kafka。两个进程复用 `creatorhub-core` 的业务代码，`creatorhub-infrastructure` 提供通用中间件配置。

MySQL 保存业务数据。Redis 保存登录会话，后续也承载缓存和派生的 Feed 数据。已在本地验证 MySQL → Canal → Kafka 的变更投递、Canal 重启续传以及身份 API，环境入口见[本地运行说明](README.md)。

## 模块设计

| 模块 | 职责 | 当前状态 |
| --- | --- | --- |
| [身份与账号](docs/design/identity/design.md) | 注册、登录、退出、修改本人昵称，提供统一用户身份 | 已实现，已通过本地双实例验收 |
| [内容](docs/design/content/design.md) | 私有草稿、文章归属、发布与阅读 | 已实现，草稿、发布及阅读验收通过 |
| [社交](docs/design/social/design.md) | 单向关注、关注状态、本人关注与粉丝列表 | 关系 API 已实现并通过本地验收 |
| Feed | 个人时间线、分发与分页 | 待细化 |
| CDC | 将数据库变更投递给 Java 消费者 | Docker 链路已验证，消费设计待细化 |
| 缓存 | 热点缓存重建与失效一致性 | 待细化 |
| Agent | 在任务授权范围内检索和保存草稿 | 后续阶段 |

身份模块提供服务端确认的 `userId`，内容和社交模块据此检查资源归属。[文章发布与按 ID 阅读](docs/design/content/design.md#文章发布与阅读)已交付。下一阶段补齐个人工作台，随后再设计发布事件和 Feed 受众规则。[社交模块的发布分发与 CDC](docs/design/social/design.md#发布分发与-cdc)

## 个人工作台

个人工作台面向当前登录用户。各入口使用对应模块的接口，服务端按每次请求的会话限定本人数据。

| 入口 | 后端接口 | 状态 |
| --- | --- | --- |
| 个人主页 | [本人资料](docs/design/identity/api.md#接口)，`GET /api/v1/me` | 复用现有接口 |
| 我的粉丝 | [粉丝列表](docs/design/social/api.md#接口)，`GET /api/v1/me/followers` | 复用现有接口 |
| 我关注的人 | [关注列表](docs/design/social/api.md#接口)，`GET /api/v1/me/following` | 复用现有接口 |
| 我的草稿箱 | [草稿接口](docs/design/content/api.md#接口)，列表为 `GET /api/v1/content/drafts` | 复用现有接口 |
| 我发布的 | [本人已发布文章列表](docs/design/content/api.md#本人已发布文章列表)，`GET /api/v1/me/articles` | 后端已实现并通过本地验收 |
