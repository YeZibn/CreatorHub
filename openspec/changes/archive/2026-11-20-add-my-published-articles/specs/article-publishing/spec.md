## ADDED Requirements

### Requirement: 作者查询本人已发布文章列表

`GET /api/v1/me/articles` SHALL 要求登录。每次请求 SHALL 从当前会话取得作者身份，且仅返回该作者的 `PUBLISHED` 文章，包括本接口上线前已发布的文章。成功响应 SHALL 返回 `200` 和 `Cache-Control: no-store`。响应体包含 `items` 和 `nextCursor`，列表项仅包含十进制字符串 `id`、`title` 和 UTC ISO 8601 格式的 `publishedAt`。

接口 SHALL 只接受可选的 `limit` 和 `cursor` 查询参数。条数默认 20，范围为 1–50。按发布时间降序及 ID 数值降序读取。首页省略游标。续页 SHALL 使用本列表返回的 URL 安全游标从上一页最后一项之后读取。游标只限定位置，所有者由当前会话决定。末页的 `nextCursor` 为 `null`，无匹配时同时返回空的 `items`。

参数未知或重复、条数不合规或游标无效时 SHALL 返回 `400 INVALID_REQUEST`。会话无效时 SHALL 返回 `401 UNAUTHENTICATED`。会话存储不可用时 SHALL 返回 `503 SESSION_UNAVAILABLE`。

#### Scenario: 发布后出现在作者的已发布列表
- **WHEN** B 发布自己的草稿并在事务提交后刷新草稿箱及已发布列表
- **THEN** 原 ID 从草稿箱消失并出现在 B 的已发布列表，摘要保留原 ID 和首次发布时间

#### Scenario: 列表归属由当前会话决定
- **WHEN** A 关注 B，且 A 使用 B 的列表游标查询自己的已发布列表
- **THEN** 结果只包含当前位置之后由 A 发布的文章，A 仍可使用现有详情接口按 ID 阅读 B 的已发布文章

#### Scenario: 同毫秒的文章稳定分页
- **WHEN** 作者翻阅同一毫秒发布的多篇文章，期间没有新增发布
- **THEN** 文章按 ID 数值降序各出现一次，最后一页没有下一页游标

#### Scenario: 作者尚未发布文章
- **WHEN** 当前登录用户只有草稿或没有内容
- **THEN** 返回 `200`、空的 `items` 和 `null` 的 `nextCursor`

#### Scenario: 分页请求参数无效
- **WHEN** 登录用户提交未知或重复的查询参数、无效条数或草稿列表游标
- **THEN** 返回 `400 INVALID_REQUEST`

#### Scenario: 缺少有效会话
- **WHEN** 访客或登录已过期的用户请求列表
- **THEN** 返回 `401 UNAUTHENTICATED`
