## Purpose

让创作者将私有草稿发布为可供登录用户按 ID 阅读的文章。发布保留内容身份、校验作者和草稿版本，并让作者在网络超时后安全重试。

## ADDED Requirements

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
