# 社交模块 API 设计

[返回社交模块](design.md) · [身份会话与错误约定](../identity/api.md#会话与错误约定)

## 接口

所有接口要求登录。写请求携带从 `GET /api/v1/auth/csrf` 取得的 CSRF 请求头。路径参数 `userId` 是正的有符号 64 位十进制整数，用于指定目标。两个写接口只接收路径参数，请求体和查询字符串均应省略。

| 方法与路径 | 成功响应 |
| --- | --- |
| `PUT /api/v1/me/following/{userId}` | `204`，已关注可关注目标或新建成功 |
| `DELETE /api/v1/me/following/{userId}` | `204`，已删除或关系已不存在 |
| `GET /api/v1/me/following/{userId}` | `200`，返回 `{"following": true}` 或 `{"following": false}` |
| `GET /api/v1/me/following` | `200`，返回我关注的人 |
| `GET /api/v1/me/followers` | `200`，返回我的粉丝 |

关注状态查询只检查当前用户到目标的关系。合法目标 ID 对应的关系不存在时返回 `false`，包括该账号不存在的情况。状态和列表响应使用 `Cache-Control: no-store`。

## 分页与响应

列表支持可选 `limit` 和 `cursor`。`limit` 默认 20，范围为 1–50。首页省略 `cursor`，续页将同一个列表返回的 `nextCursor` 原样传回。游标只限定位置，所有者仍以当前会话为准。

排序为 `followedAt DESC, userId DESC`，同时间项按用户 ID 的数值排序。接口以字符串返回 ID，关注时间使用 UTC ISO 8601。以下示例为最后一页。

```json
{
  "items": [
    {
      "userId": "42",
      "displayName": "春日手记",
      "followedAt": "2026-11-20T09:00:00.123Z"
    }
  ],
  "nextCursor": null
}
```

## 错误

响应体包含 `code` 和 `message`。

| HTTP 状态 | 错误码 | 条件 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 目标 ID 非法或为本人、写请求包含额外输入、条数或游标无效、游标用于另一列表方向 |
| `401` | `UNAUTHENTICATED` | 缺少有效登录会话 |
| `403` | `CSRF_INVALID` | 写请求缺少有效 CSRF Token。此检查可能先于认证 |
| `404` | `FOLLOW_TARGET_NOT_FOUND` | 发起关注时目标账号不存在或已停用 |
| `503` | `SESSION_UNAVAILABLE` | 无法访问会话存储 |
