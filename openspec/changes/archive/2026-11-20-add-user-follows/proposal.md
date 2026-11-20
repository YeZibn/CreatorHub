## Why

用户需要关注感兴趣的创作者，并查看自己建立和收到的关注关系。先把关系及其查询做稳定，后续发布分发就可以按作者查询候选接收者。

## What Changes

- 新增单向关注、取消关注、查询关注状态，以及本人关注和粉丝的游标分页 API。
- 用会话确定操作者，由数据库保证同一方向的关系唯一，支持安全重复提交。
- 在关系写入开放前从 Canal 白名单移除 `follow`，保留现有验证表。内容发布、关系事件、Feed 和公开作者入口留给后续变更。

## Capabilities

### New Capabilities

- `user-follows` 覆盖用户单向关注、本人关系列表及关注数据的分发边界。

### Modified Capabilities

无。

## Impact

- `creatorhub-api` 和 `creatorhub-core` 新增社交模块，复用[身份会话与 CSRF 约定](../../../../docs/design/identity/api.md)。
- MySQL 新增 `follow` 表和双向访问索引，引用现有账号与资料。
- [Compose](../../../../compose.yaml) 收紧 Canal 白名单。首期使用两个测试账号和已知用户 ID 验收，不依赖用户搜索或前端入口。
