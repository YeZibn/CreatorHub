# article-publishing Specification

## Purpose

让创作者将私有草稿发布为可供登录用户按 ID 阅读的文章。发布保留内容身份、校验作者和草稿版本，并让作者在网络超时后安全重试。

## Requirements

### Requirement: 作者发布已保存的草稿

`POST /api/v1/content/drafts/{id}/publish` SHALL 要求登录并通过 CSRF 校验。目标 ID SHALL 是正的十进制内容 ID，请求体 SHALL 只接受正整数 `version`，沿用草稿接口的版本范围和身份错误约定。请求参数不合规 SHALL 返回 `400 INVALID_REQUEST`。系统 SHALL 从会话取得作者，对不存在或属于其他账号的内容返回 `404 DRAFT_NOT_FOUND`。

对于属于作者的 `DRAFT`，提交版本不匹配时 SHALL 返回 `409 CONTENT_VERSION_CONFLICT`。版本匹配后，已保存的标题和正文都 SHALL 包含非空白字符，否则返回 `400 INVALID_REQUEST` 并保留草稿。成功时 SHALL 在原内容 ID 上完成 `DRAFT` 到 `PUBLISHED` 的转换，版本加一并记录首次发布时间。事务提交后 SHALL 返回 `200`，包含字符串 `id`、字符串 `authorId`、`title`、`body`、`publishedAt` 和新的 `version`。响应 SHALL 使用 `Cache-Control: no-store`。

#### Scenario: 发布本人草稿
- **WHEN** 作者携带正确版本发布标题和正文齐全的草稿
- **THEN** 返回 `200`，内容 ID、标题和正文保持原值，版本递增一次并生成发布时间，原记录退出草稿列表

#### Scenario: 草稿仍可继续编辑
- **WHEN** 作者发布标题或正文为空白的当前版本草稿
- **THEN** 返回 `400 INVALID_REQUEST`，状态、内容和版本保持原样

#### Scenario: 他人尝试发布
- **WHEN** A 使用有效会话和 CSRF Token 发布 B 的草稿或已发布文章
- **THEN** 返回 `404 DRAFT_NOT_FOUND`，响应不暴露作者、版本或发布状态

### Requirement: 发布竞争与同版本重试

发布和草稿保存竞争同一版本时 SHALL 仅允许一方完成相应写入。若草稿保存先提交，旧版本发布 SHALL 返回 `409 CONTENT_VERSION_CONFLICT`。若发布先提交，草稿保存沿用原接口对已离开草稿状态的处理。

已发布时 SHALL 仅向作者提供同请求版本的重试成功响应。重试版本等于首次发布所提交的草稿版本时 SHALL 返回相同的 `200` 响应及首次发布时间，且不重复写入。其他版本的发布请求 SHALL 返回 `409 CONTENT_VERSION_CONFLICT`。

#### Scenario: 旧标签页发布与保存竞争
- **WHEN** 一个标签页保存新版本，另一标签页随后提交旧版本发布
- **THEN** 发布返回 `409 CONTENT_VERSION_CONFLICT`，最新草稿保持私有

#### Scenario: 两个请求发布同一个版本
- **WHEN** 作者同时提交两个版本相同的有效发布请求，或首次提交成功后因超时重试
- **THEN** 只有一次状态转换和版本递增，两个请求获得相同文章与发布时间

### Requirement: 登录用户按原 ID 阅读已发布文章

`GET /api/v1/content/articles/{id}` SHALL 要求登录。任一登录用户可读取 `PUBLISHED` 内容，响应 SHALL 返回 `200` 和字符串 `id`、字符串 `authorId`、`title`、`body`、`publishedAt`。发布时间 SHALL 使用 UTC ISO 8601 格式。内容不存在或仍是草稿时 SHALL 返回 `404 ARTICLE_NOT_FOUND`。响应 SHALL 使用 `Cache-Control: no-store`。

#### Scenario: 使用同一个 ID 从草稿到可读文章
- **WHEN** 登录读者在作者发布之前和之后分别请求同一内容 ID
- **THEN** 发布前返回 `404`，提交成功后返回同一 ID 的文章，读者的关注关系不影响此结果

### Requirement: 发布阶段维持内容的 CDC 边界

本阶段的草稿及发布操作 SHALL 不向 Kafka 投递内容行变更或发布事件。既有 CDC 验证链路 SHALL 保持有效。

#### Scenario: 正常 CDC 链路下完成发布
- **WHEN** 创建、编辑并发布带测试标记的内容，随后写入独立 CDC 验证标记
- **THEN** Kafka 收到验证标记，期间没有内容行变更、发布事件或正文测试标记

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
