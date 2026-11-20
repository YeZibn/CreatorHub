# CreatorHub 架构总纲

CreatorHub 是面向创作者的内容社区。用户可以写文章、关注作者并浏览个人 Feed。Java 负责账号、业务数据和内容分发。后续 Python Agent 通过 Java API 检索内容和保存草稿，由用户确认发布。

## 运行边界

Java 后端计划采用 Spring MVC、原生 MyBatis 和四个 Maven 子模块。`creatorhub-api` 提供 HTTP 接口，`creatorhub-consumer` 订阅 Kafka。两个进程复用 `creatorhub-core` 的业务代码，`creatorhub-infrastructure` 提供通用中间件配置。

MySQL 保存业务数据。Redis 保存登录会话、缓存和派生的 Feed 数据。已在本地验证 MySQL → Canal → Kafka 的变更投递和 Canal 重启后续传，环境入口见 [Docker 运行说明](README.md)。Java 业务模块处于设计阶段。

## 模块设计

| 模块 | 职责 | 当前状态 |
| --- | --- | --- |
| [身份与账号](docs/design/identity/design.md) | 注册、登录、退出、修改本人昵称，提供统一用户身份 | 设计已确认，待实现 |
| 内容 | 草稿、发布、下架和文章归属 | 待细化 |
| 社交 | 关注和互动关系 | 待细化 |
| Feed | 个人时间线、分发与分页 | 待细化 |
| CDC | 将数据库变更投递给 Java 消费者 | Docker 链路已验证，消费设计待细化 |
| 缓存 | 热点缓存重建与失效一致性 | 待细化 |
| Agent | 在任务授权范围内检索和保存草稿 | 后续阶段 |

先实现身份模块，使后续受保护的请求都能取得服务端确认的 `userId`，再完成内容、关注和基础 Feed 的闭环。资源归属与可见性规则由各业务模块维护。
