## MODIFIED Requirements

### Requirement: 分页查看本人关注与粉丝

`GET /api/v1/me/following` SHALL 返回当前用户关注的人，`GET /api/v1/me/followers` SHALL 返回当前用户的粉丝。两者 SHALL 返回 `items` 和 `nextCursor`。摘要 SHALL 包含对端的字符串 `userId`、`displayName` 和 UTC ISO 8601 格式的 `followedAt`。结果 SHALL 按 `followedAt DESC, userId DESC` 排序，用户 ID 使用数值顺序。响应 SHALL 使用 `Cache-Control: no-store`。

`limit` SHALL 默认为 20，范围 1 至 50。后续页 SHALL 接受相应列表方向的 URL 安全不透明游标。最后一页的 `nextCursor` SHALL 为 `null`。无效条数、无效游标或游标用于另一列表方向 SHALL 返回 `400 INVALID_REQUEST`。每一页 SHALL 从当前会话确定列表所有者。

关注或取关事务提交后开始的列表请求 SHALL 按已提交的关系读取。与关系变更重叠的请求可返回其已取得的关系快照。昵称 SHALL 从当前资料读取。业务缓存不可用或数据无效时，已通过会话验证的列表查询 SHALL 回源，并遵循相同的权限、排序和分页规则。会话存储不可用时 SHALL 返回 `503 SESSION_UNAVAILABLE`。

#### Scenario: 同毫秒的关注跨页
- **WHEN** 多条关系具有相同的时间戳，调用者依次翻页且期间没有关系变化
- **THEN** 每条关系恰好出现一次，同时间项按对端 ID 数值降序

#### Scenario: 不同账号使用同方向游标
- **WHEN** A 把 B 的有效关注列表游标用于自己的关注列表
- **THEN** 服务端仍只返回 A 在游标边界后的关注关系

#### Scenario: 取关提交后旧首页延迟回填
- **WHEN** A 取关 B 并成功提交，提交前读到旧关系的请求随后回填，且没有再次建立该关系
- **THEN** 提交后开始的 A 的关注列表以及 B 的粉丝列表请求均不返回这条关系

#### Scenario: 改昵称后读取已缓存的关系
- **WHEN** 对端更新昵称并提交，列表所有者随后读取含该关系的首页
- **THEN** 首页返回更新后的昵称和原关注时间

#### Scenario: 业务缓存故障时读取列表
- **WHEN** 当前会话有效、关系数据源可用，但首页业务缓存读取失败或内容无效
- **THEN** 仍返回 `200` 及当前账号对应方向的列表，字段及分页行为符合接口契约

#### Scenario: 会话存储不可用
- **WHEN** 会话存储无法验证请求携带的登录会话
- **THEN** 返回 `503 SESSION_UNAVAILABLE`
