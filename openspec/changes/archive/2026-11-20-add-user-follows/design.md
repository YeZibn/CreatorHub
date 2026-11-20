## Context

现有身份模块用 Redis 会话提供服务端认证身份。MySQL 已有 `user_account`、`user_profile` 和草稿迁移。[Compose](../../../../compose.yaml) 的 Canal 白名单包含 `follow` 和 `cdc_smoke_probe`，因此新表启用前要调整采集范围。业务范围见[提案](proposal.md)，对外行为见[增量规格](specs/user-follows/spec.md)。

## Goals / Non-Goals

- 关系以 MySQL 为事实来源，两个方向都可按索引分页，写入成功后可以查询到已提交状态。
- 关系表维护当前状态。发布时的受众快照、取关与异步分发的先后规则、历史回填与关系事件协议留到发布和 Feed 的设计阶段。

## Decisions

### 有向关系与访问路径

Flyway V3 创建 InnoDB 表 `follow`。两端都是 `BIGINT NOT NULL`，引用 `user_account.id`，外键删除策略为 `RESTRICT`。使用 `CHECK (follower_id <> followee_id)`，`created_at` 使用 UTC 会话的 `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`。

| 索引 | 对应查询 |
| --- | --- |
| `PRIMARY KEY (follower_id, followee_id)` | 精确读写一条关系并保证唯一 |
| `(follower_id, created_at DESC, followee_id DESC)` | 查询我的关注 |
| `(followee_id, created_at DESC, follower_id DESC)` | 查询我的粉丝，为后续枚举候选接收者提供同一访问路径 |

复合主键直接表达关系身份，可以省去额外的自增主键和重复的唯一索引。取关删除对应行，表内保留当前有效关系。

### 身份与事务

`creatorhub-api` 的社交 Controller 获取当前会话的 `userId`，校验目标与游标，复用已有 CSRF 和错误结构。`creatorhub-core` 的社交 Service 负责事务，MyBatis Mapper 负责写入与列表查询。

关注时，在同一事务按主键对目标账号做 `FOR SHARE` 当前读，校验其为 `ACTIVE`，随后用主键约束裁决并发插入。可用 `INSERT ... ON DUPLICATE KEY UPDATE created_at = created_at` 实现幂等写入，保留已有时间。删除及状态查询始终绑定会话用户与目标两列。取消请求无需检查目标状态。账号停用限制新的关注写入，已有关系仍按表中记录查询和移除，账号公共可见性政策随公开入口定义。Redis 继续提供会话，关系写入依赖 MySQL 短事务。

### 稳定分页

两份列表都按时间及对端 ID 降序查询，使用 `limit + 1` 确定下一页。后续页限定 `created_at < cursorTime OR (created_at = cursorTime AND peerId < cursorId)`，并保持会话所有者的等值谓词。游标编码列表方向、UTC 毫秒时间和对端 ID。列表按查询到的关系补充对应的昵称，响应只包含对端 ID、昵称和关注时间。

### CDC 边界

关系数据暂留 MySQL。关系写入由 API 同步提交。移除 `follow` 后保留 `cdc_smoke_probe`，草稿表继续排除在外。后续若需要根据关系变更维护派生数据，再设计事件字段、顺序、重复消费和恢复。

## Risks / Trade-offs

- 相反意图并发到达 → 关注与取关结果取决于数据库实际执行顺序。调用方遇到超时可读取当前状态，重试应服从用户最新意图。
- 实时分页期间关系变化 → 新关注可能排在已翻过的位置，重复关注和普通读取不会改变时间。客户端刷新首页即可获得新排序。
- Kafka 观察为空可能源自采集停机 → 记录起始位点，在 API 写入后提交验证标记，从同一分区读到该标记再判定关系行有无泄露。

## Migration Plan

1. 收紧 Compose 的 Canal 白名单并重建实例，检查实际过滤规则。
2. 在已有 V2 的数据库上应用 V3，检查两端外键、约束与索引，再开放 API 写入。
3. 如需回退 API，保留关系表与当前过滤配置。部署旧版前核对该制品的 Flyway 迁移校验是否兼容已应用的 V3。
