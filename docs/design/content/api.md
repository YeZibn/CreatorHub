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
