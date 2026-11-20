## Why

CreatorHub 需要一套稳定的用户身份，让后续的文章归属、关注关系和个人 Feed 都能从服务端取得可信的 `userId`。先完成本地可验收的账号闭环，为其他业务模块建立共同的鉴权入口。

## What Changes

- 新增邮箱密码注册、登录、退出和修改本人昵称。
- 提供 `/me` 查询和 CSRF Token 辅助接口。两个 API 实例共享登录会话，退出只撤销当前设备的会话。
- 按已确认的设计使用 `user_account` 与 `user_profile`，共享用户 ID。注册事务同时创建两条记录，邮箱唯一约束裁决并发重复注册。
- 第一阶段用于本地演示。邮箱验证、找回密码、主动改密、头像和其他内容业务放在后续变更。

## Capabilities

### New Capabilities

- `user-identity` 约定账号注册、会话认证和撤销，以及个人资料的访问和修改边界。

### Modified Capabilities

无。

## Impact

- API 新增 `/api/v1/auth/*` 和 `/api/v1/me*` 中由 [身份 API 设计](../../../../docs/design/identity/api.md) 定义的接口。
- Java 新增身份 Service 和 MyBatis Mapper，API 接入 Spring Security 和 Spring Session 的 Redis 会话能力。
- MySQL 新增两张身份表。数据库迁移沿用[身份数据库设计](../../../../docs/design/identity/database.md)，账号凭证及资料不加入现有 CDC 白名单。
