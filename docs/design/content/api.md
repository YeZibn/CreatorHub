# 内容模块 API 设计

[返回内容模块](design.md) · [身份会话与错误约定](../identity/api.md#会话与错误约定)

## 接口

所有接口均要求登录，服务端从会话获取作者身份。两个写接口使用 `application/json`，提交前从 `GET /api/v1/auth/csrf` 获取并携带返回的 CSRF 请求头。草稿数据响应带有 `Cache-Control: no-store`。

| 方法与路径 | 输入 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/content/drafts` | JSON `title`、`body` | `201`，完整草稿，初始 `version` 为 1 |
| `GET /api/v1/content/drafts` | 可选查询参数 `limit`、`cursor` | `200`，`items` 和 `nextCursor` |
| `GET /api/v1/content/drafts/{id}` | 内容 ID | `200`，完整草稿 |
| `PUT /api/v1/content/drafts/{id}` | 内容 ID，JSON `title`、`body`、`version` | `200`，完整草稿，版本加一 |

## 请求约束

- `title`、`body` 都必须是字符串。去除标题首尾空白后最多 200 个 Unicode 码点，正文最多 20,000 个 Unicode 码点并保留原文。两项各自允许为空字符串，至少一项包含非空白字符。
- `version` 是正的 JSON 整数，范围与数据库的 `INT UNSIGNED` 一致。客户端使用详情或上次保存响应中的版本。
- 拒绝缺失字段、`null`、类型错误和未定义字段，例如 `authorId` 或 `status`。
- `id` 对客户端是十进制字符串，读取、保存及生成路径时应按字符串传递。

## 响应示例

草稿详情，适用于创建、读取和修改。

```json
{
  "id": "42",
  "title": "春日创作手记",
  "body": "# 春日创作手记\n\n今天完成第一段。",
  "version": 1,
  "createdAt": "2026-11-20T08:00:00.123Z",
  "updatedAt": "2026-11-20T08:00:00.123Z"
}
```

草稿列表的最后一页。摘要只包含 `id`、`title`、`updatedAt`。时间均为 UTC ISO 8601。

```json
{
  "items": [
    {"id": "42", "title": "春日创作手记", "updatedAt": "2026-11-20T08:00:00.123Z"}
  ],
  "nextCursor": null
}
```

`limit` 默认 20，接受 1–50。首页省略 `cursor`，后续页将服务端返回的非空 `nextCursor` 原样传回。游标是 URL 安全的不透明字符串，由上一页最后一项的修改时间和 ID 编码得到。排序固定为 `updatedAt DESC, id DESC`。游标只影响读取位置，每页使用当前登录账号重新过滤。

## 错误响应

响应体包含 `code` 和 `message`。错误消息不包含草稿正文。

| 状态 | 错误码 | 触发条件 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 请求体、路径 ID 格式、条数或游标无效 |
| `401` | `UNAUTHENTICATED` | 会话缺失、过期或已注销 |
| `403` | `CSRF_INVALID` | 写请求缺少有效 CSRF Token。CSRF 校验可能先于登录检查 |
| `404` | `DRAFT_NOT_FOUND` | 合法请求指定的草稿不存在、属于其他账号，或已离开 `DRAFT` 状态 |
| `409` | `CONTENT_VERSION_CONFLICT` | 当前账号拥有该草稿，但提交的版本已经过期 |
| `503` | `SESSION_UNAVAILABLE` | 无法访问服务端会话存储 |

收到 `409` 后，编辑器保留当前输入。作者主动重载时重新请求详情，由新的读取结果提供正文和版本。

## 发布与阅读接口

[文章发布与阅读](design.md#文章发布与阅读)已实现。两接口要求登录，响应使用 `Cache-Control: no-store`。

| 方法与路径 | 输入与权限 | 成功响应 |
| --- | --- | --- |
| `POST /api/v1/content/drafts/{id}/publish` | 正数内容 ID、JSON `{"version": 3}`。仅作者可发布，携带 CSRF Token | `200`，返回沿用原 ID 的文章、发布时间和递增后的版本 |
| `GET /api/v1/content/articles/{id}` | 内容 ID，任何登录用户可读 | `200`，返回文章的 ID、作者 ID、标题、正文和发布时间 |

- 发布只接收 `version`，必须是正 JSON 整数，范围与数据库 `INT UNSIGNED` 一致。额外请求字段、无效 ID、版本类型或范围错误返回 `400 INVALID_REQUEST`。
- 发布时使用最后一次成功保存的标题和正文，两者都须至少含一个非空白字符。任一为空白时返回 `400 INVALID_REQUEST`，草稿保持可编辑。
- 发布成功返回字符串 `id` 和 `authorId`、`title`、`body`、UTC ISO 8601 格式的 `publishedAt` 及递增后的 `version`。文章详情返回相同内容字段，但不包含版本。
- 非作者访问发布接口，或阅读不存在、仍为 `DRAFT` 的内容，分别统一返回 `404 DRAFT_NOT_FOUND` 和 `404 ARTICLE_NOT_FOUND`。已发布文章的 ID 不再匹配草稿详情、草稿列表或草稿更新。
- 同一作者在已发布后使用首次发布的草稿版本重试，返回相同的发布响应和首次发布时间，不再改变记录。其他版本返回 `409 CONTENT_VERSION_CONFLICT`。

发布响应示例：

```json
{
  "id": "42",
  "authorId": "7",
  "title": "春日创作手记",
  "body": "# 春日创作手记\n\n今天完成第一段。",
  "publishedAt": "2026-11-20T08:00:00.123Z",
  "version": 2
}
```

文章详情响应去掉版本字段。关注关系不影响登录用户按 ID 阅读。

| 状态 | 错误码 | 触发条件 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 发布 ID、JSON 字段或发布必填内容不合规 |
| `401` | `UNAUTHENTICATED` | 登录会话缺失或过期 |
| `403` | `CSRF_INVALID` | 发布请求缺少有效 CSRF Token。CSRF 校验可能先于认证 |
| `404` | `DRAFT_NOT_FOUND` | 目标草稿不存在或属于其他作者 |
| `404` | `ARTICLE_NOT_FOUND` | 阅读目标不存在或尚未发布 |
| `409` | `CONTENT_VERSION_CONFLICT` | 发布请求提交了过期版本，或重复请求使用的版本不是首次发布版本 |
