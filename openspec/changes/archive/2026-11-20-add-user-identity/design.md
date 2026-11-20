## Context

项目已有 MySQL、Redis、Kafka 和 Canal 的本地 Docker 环境，Java 业务代码从身份模块开始落地。采用[现有架构](../../../../ARCHITECTURE.md)、[身份模块设计](../../../../docs/design/identity/design.md)和[身份 API 契约](../../../../docs/design/identity/api.md)。

## Goals / Non-Goals

**Goals**

- 将服务端认证身份作为 HTTP 接口和业务 Service 的用户身份来源。
- 支持多 API 实例共享浏览器会话，并验证会话轮换、撤销、故障时的错误响应和资料归属。
- 让数据库约束和事务承担账号唯一性及资料创建的一致性。

**Non-Goals**

- Java Consumer、内容、关注、Feed 和 Agent 实现随各自的变更交付。Java 工程此次只接入身份链路所需的模块和组件。
- 公开注册验证与账号恢复留给后续变更，范围见[提案](proposal.md)。

## Decisions

1. **Spring MVC 的 HTTP 入口与身份业务分层。** `creatorhub-api` 管 Spring Security、Cookie、CSRF 和 DTO。`creatorhub-core` 管身份 Service、Entity 和原生 MyBatis Mapper，`creatorhub-infrastructure` 提供共享 Redis 连接。身份 Service 只接收服务端解析的当前用户 ID。这样各业务模块都能复用同一身份，普通消费者也无需依赖 HTTP 会话。
2. **浏览器使用 Redis 服务端会话。** 同源 Vue 通过 Cookie 持有会话 ID。Spring Session 让两个实例共享会话，退出可以直接删除当前会话。独立实例内存会话需要请求粘滞，自包含令牌需要额外设计提前撤销，当前选择服务端会话。JSON 登录应接入 Spring Security 的会话策略和安全上下文保存机制，保存完成后再返回成功。退出后获准执行的旧在途请求可以完成，后续请求无法恢复该会话。
3. **账号和资料分表且共用主键。** 账号按邮箱验证凭证，资料按用户 ID 查询和更新。共用主键使公开资料路径可以单独读取资料表，代价是 `/me` 需要跨两张表读取。按[数据库设计](../../../../docs/design/identity/database.md)使用两个主键、邮箱唯一索引和外键。注册在同一事务内取得自增 ID 并创建资料，通过唯一约束处理并发冲突。
4. **凭证校验与资料授权独立。** 密码用 Argon2id 哈希。当前用户查询和改昵称从认证上下文提取 ID。资料更新使用 `WHERE user_id = ?`，请求 DTO 只允许约定字段，响应 DTO 仅投影已声明的数据。

## Risks / Trade-offs

- Redis 不可用会中断会话读写 → 按接口契约返回 `503`，不给出登录或退出成功的响应。
- 会话配置或 CSRF 接线错误可能只在跨实例或登录前后出现 → 使用真实 Redis 和两个 API 实例，覆盖 Cookie 轮换、退出后旧 Cookie 全实例失效及 CSRF Token 重新获取。
- 注册前预查邮箱会与并发写入竞争 → 数据库唯一约束作为裁决依据，只将对应的唯一键冲突映射为邮箱重复。

## Migration Plan

复用现有 Docker 基础设施。首个身份迁移按账号表、资料表的顺序执行，迁移通过后再启动 API。用测试数据验证登录、`/me` 和退出，随后接入注册及改昵称。回退应用时保留已创建的表和数据。

## Open Questions

会话超时与 Argon2id 的具体参数在实现时配置，通过本地会话过期和登录压测确认。依赖的补丁版本在构建和联调时固定。
