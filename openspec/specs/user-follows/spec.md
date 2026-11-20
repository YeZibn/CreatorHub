# User Follows Specification

## Purpose

让 CreatorHub 用户建立和取消单向关注，并查看自己的关注和粉丝。系统保护本人关系数据，提供幂等写入与稳定分页，为后续文章分发提供当前关系查询。

## Requirements

### Requirement: 按当前登录身份管理单向关注

`PUT /api/v1/me/following/{userId}` SHALL 使当前用户关注指定用户。目标须为存在且状态为 `ACTIVE` 的其他账号。成功建立关系或对同一可关注目标重复提交 SHALL 返回 `204`。同一方向 SHALL 最多有一条关系，重复请求 SHALL 保留原关注时间。目标不存在或停用时 SHALL 返回 `404 FOLLOW_TARGET_NOT_FOUND`。

`DELETE /api/v1/me/following/{userId}` SHALL 删除当前用户到目标的关系。关系已不存在时仍 SHALL 返回 `204`，目标后来停用时也可取消关注。取消后重新关注 SHALL 建立新关系并记录此次关注时间。

所有关系接口 SHALL 使用服务端会话确认访问者，写请求 SHALL 通过 CSRF 校验，成功写响应 SHALL 在事务提交后返回。目标 ID SHALL 是能用正的有符号 64 位整数表示的十进制字符串。无效目标 ID、对自己的目标请求、写入额外字段 SHALL 返回 `400 INVALID_REQUEST`。身份与 CSRF 错误 SHALL 沿用已有约定。

#### Scenario: 并发关注同一个人
- **WHEN** 同一个用户同时发起多个请求关注同一可关注目标
- **THEN** 成功的请求返回 `204`，两个账号之间的该方向只有一条关系，后续重复请求保持原关注时间

#### Scenario: 反向关系独立
- **WHEN** A 关注 B，B 也关注 A，随后 A 重复取消对 B 的关注
- **THEN** A 的取关请求返回 `204`，仅移除 A 到 B 的关系，B 到 A 的关系仍存在

#### Scenario: 自关注或指定其他发起者
- **WHEN** A 尝试关注自己，或在写请求中提交发起者字段
- **THEN** 返回 `400 INVALID_REQUEST`，关系保持原状

#### Scenario: 认证与目标校验
- **WHEN** 未登录、写请求缺少有效的 CSRF Token，或已登录用户试图关注不存在或停用的目标
- **THEN** 按对应失败原因返回 `401 UNAUTHENTICATED`、`403 CSRF_INVALID` 或 `404 FOLLOW_TARGET_NOT_FOUND`，不写入关系

### Requirement: 查询自己的关注状态

`GET /api/v1/me/following/{userId}` SHALL 返回 `200` 和布尔字段 `following`，表示当前登录者到目标的关系是否存在。合法的其他用户 ID 若没有相应关系，SHALL 返回 `false`。该接口 SHALL 使用 `Cache-Control: no-store`。

#### Scenario: 两个方向分别查询
- **WHEN** A 关注 B，而 B 没有关注 A，双方查询各自对对方的关注状态
- **THEN** A 得到 `true`，B 得到 `false`

### Requirement: 分页查看本人关注与粉丝

`GET /api/v1/me/following` SHALL 返回当前用户关注的人，`GET /api/v1/me/followers` SHALL 返回当前用户的粉丝。两者 SHALL 返回 `items` 和 `nextCursor`。摘要 SHALL 包含对端的字符串 `userId`、`displayName` 和 UTC ISO 8601 格式的 `followedAt`。结果 SHALL 按 `followedAt DESC, userId DESC` 排序，用户 ID 使用数值顺序。响应 SHALL 使用 `Cache-Control: no-store`。

`limit` SHALL 默认为 20，范围 1 至 50。后续页 SHALL 接受相应列表方向的 URL 安全不透明游标。最后一页的 `nextCursor` SHALL 为 `null`。无效条数、无效游标或游标用于另一列表方向 SHALL 返回 `400 INVALID_REQUEST`。每一页 SHALL 从当前会话确定列表所有者。

#### Scenario: 同毫秒的关注跨页
- **WHEN** 多条关系具有相同的时间戳，调用者依次翻页且期间没有关系变化
- **THEN** 每条关系恰好出现一次，同时间项按对端 ID 数值降序

#### Scenario: 不同账号使用同方向游标
- **WHEN** A 把 B 的有效关注列表游标用于自己的关注列表
- **THEN** 服务端仍只返回 A 在游标边界后的关注关系

### Requirement: 关注关系写入不进入分发消息

当前阶段的关注与取关 SHALL 不向 Kafka 投递关注关系行变更。既有 CDC 验证链路 SHALL 继续投递验证标记。

#### Scenario: 关注写入时 CDC 链路正常
- **WHEN** 通过 API 关注并取关，随后写入独立的 CDC 验证标记
- **THEN** Kafka 收到该标记，观察区间内无关注关系行变更
