## Purpose

让 CreatorHub 的创作者能暂存并继续编辑未发布的内容，保证草稿只对作者可见，并防止不同页面的并发保存覆盖已经提交的修改。

## ADDED Requirements

### Requirement: 创建私有草稿

`POST /api/v1/content/drafts` SHALL 接受字符串字段 `title` 和 `body`。标题去除首尾空白后 SHALL 不超过 200 个 Unicode 码点，正文 SHALL 原样保留并且不超过 20,000 个 Unicode 码点。两项可单独为空字符串，至少一项 SHALL 包含非空白内容。系统 SHALL 以当前会话的用户身份作为作者，将新记录保存为 `DRAFT`，初始版本为 `1`。提交成功后 SHALL 返回 `201` 和草稿详情。

#### Scenario: 首次保存只有标题的草稿
- **WHEN** 登录用户提交有效标题和空正文
- **THEN** 系统保存一条属于该用户的草稿，返回 `201`、字符串 `id`、标题、正文、版本与创建和修改时间

#### Scenario: 第一稿为空或字段不合规
- **WHEN** 创建请求的标题和正文都为空白，字段缺失、为 null、超长或含有未定义字段
- **THEN** 返回 `400 INVALID_REQUEST`，不创建草稿

### Requirement: 以作者身份访问私有草稿

草稿入口 SHALL 要求登录，创建和修改 SHALL 校验 CSRF Token。读取、列表和修改 SHALL 使用服务端会话解析的作者身份。详情 `GET /api/v1/content/drafts/{id}` SHALL 只返回当前用户自己的草稿。草稿数据响应 SHALL 使用 `Cache-Control: no-store`，业务日志 SHALL 省略草稿正文。

#### Scenario: 作者重新打开草稿
- **WHEN** 作者使用自己的会话查询已成功保存的草稿
- **THEN** 返回 `200` 和 `id`、`title`、`body`、`version`、`createdAt`、`updatedAt`

#### Scenario: 目标草稿不可访问
- **WHEN** 已登录用户读取或提交修改，而 ID 不存在、属于其他作者或已经不是草稿
- **THEN** 对合法格式的请求统一返回 `404 DRAFT_NOT_FOUND`，响应不暴露归属或版本

#### Scenario: 缺失认证或 CSRF Token
- **WHEN** 未登录用户访问草稿接口，或写操作没有有效的 CSRF Token
- **THEN** 按身份接口约定返回 `401 UNAUTHENTICATED` 或 `403 CSRF_INVALID`，且不修改草稿

### Requirement: 分页查看个人草稿列表

`GET /api/v1/content/drafts` SHALL 按 `updatedAt DESC, id DESC` 返回当前作者的草稿。列表 SHALL 返回 `items` 和 `nextCursor`，每项包含 `id`、`title`、`updatedAt`。接口 SHALL 接受可选的 `limit` 和 `cursor`，默认 20 条，允许 1 至 50 条。无下一页时 `nextCursor` SHALL 为 null。无效游标或非法条数 SHALL 返回 `400 INVALID_REQUEST`。

#### Scenario: 同一毫秒的草稿跨页
- **WHEN** 作者的多条草稿拥有相同的修改时间，在列表内容未变化时依次请求各页
- **THEN** 服务端用 ID 决定同时间项的顺序，每条草稿恰好出现一次

#### Scenario: 游标来自不同账号
- **WHEN** 用户 A 提交了 B 的列表游标
- **THEN** 查询仍以 A 的会话身份过滤，响应不包含 B 的草稿

### Requirement: 手动保存与版本冲突

`PUT /api/v1/content/drafts/{id}` SHALL 接受 `title`、`body` 和正整数 `version`，对标题与正文沿用创建规则。系统 SHALL 只在草稿仍归当前作者且版本匹配时保存，版本加一并更新修改时间。成功提交后 SHALL 返回 `200` 和新草稿详情。本人草稿版本不匹配 SHALL 返回 `409 CONTENT_VERSION_CONFLICT`。

#### Scenario: 两个页面提交同一版本
- **WHEN** 同一作者的两个页面以相同草稿版本提交不同的正文
- **THEN** 恰好一个请求成功并使版本加一，另一个请求返回 `409`，持久化内容对应成功的请求

#### Scenario: 编辑器收到冲突
- **WHEN** 用户手动保存时收到 `409`
- **THEN** 编辑器保留未保存的输入并提示冲突，只有用户选择重新加载才替换本地正文

### Requirement: 草稿不进入分发消息

当前草稿阶段，草稿的创建和编辑 SHALL 不向 Kafka 投递草稿行变更，既有 CDC 验证链路 SHALL 仍可使用。

#### Scenario: 在正常投递的 CDC 链路中保存草稿
- **WHEN** 使用可识别的测试内容创建并修改草稿，随后写入独立 CDC 验证标记
- **THEN** Kafka 收到验证标记，期间未投递草稿行变更或测试正文
