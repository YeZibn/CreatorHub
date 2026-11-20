## Why

反复打开个人工作台会重复查询同一账号的关注和粉丝首页。增加可重建的 Redis 缓存，可以验证是否能降低列表查询成本，同时保持关注或取关成功后的读取结果正确。

## What Changes

- 为「我的粉丝」和「我关注的人」增加可配置的 Redis ZSet 首页读路径。会话已验证且业务缓存不可用时回源 MySQL。
- 通过 Flyway V6 增加 `follow_list_revision`。实际关系变化与两个受影响方向的版本在同一事务提交，防止旧快照晚回填后被新请求读取。
- 对照纯 MySQL 路径验证排序、分页、账号隔离和故障行为，并测量缓存对读延迟及关系写入竞争的影响。

## Capabilities

### New Capabilities

### Modified Capabilities

- `user-follows`：为本人关注与粉丝列表明确写入提交后的可见性、昵称更新和业务缓存故障回源行为。

## Impact

- `creatorhub-api` 沿用现有五个关系端点及游标。 `creatorhub-core` 增加关系版本事务与首页查询协调，`creatorhub-infrastructure` 提供业务缓存读写、Lua 和超时配置。
- 上线需要 V6 迁移以及所有关系写入实例升级。首版复用现有 Redis，首页推荐和 Feed 分发分别交付。 [社交缓存设计](../../../../docs/design/social/cache.md)
