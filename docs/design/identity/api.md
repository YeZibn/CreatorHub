# 身份模块 API 设计

回到 [身份模块设计](design.md) · [数据库设计](database.md)

## 接口

路径统一使用 `/api/v1` 前缀。JSON 中的 `userId` 以字符串返回。

| 方法和路径 | 请求字段 | 成功响应 | 主要错误 |
| --- | --- | --- | --- |
| `POST /api/v1/auth/register` | `email`、`password`、`displayName` | `201`，返回 `userId` | `400 INVALID_REQUEST`、`409 EMAIL_ALREADY_REGISTERED` |
| `POST /api/v1/auth/login` | `email`、`password` | `200`，设置会话 Cookie，返回 `userId`、`displayName` | `400 INVALID_REQUEST`、`401 INVALID_CREDENTIALS`、`503 SESSION_UNAVAILABLE` |
| `POST /api/v1/auth/logout` | 无 | `204`，撤销当前会话并清理 Cookie | `401 UNAUTHENTICATED`、`503 SESSION_UNAVAILABLE` |
| `PATCH /api/v1/me/profile` | `displayName` | `200`，返回 `userId`、`displayName` | `400 INVALID_REQUEST`、`401 UNAUTHENTICATED` |
| `GET /api/v1/me` | 无 | `200`，返回 `userId`、`email`、`displayName` | `401 UNAUTHENTICATED`、`503 SESSION_UNAVAILABLE` |
| `GET /api/v1/auth/csrf` | 无 | `200`，返回 `headerName` 和 `token` | `503 SESSION_UNAVAILABLE` |

邮箱按[数据库设计](database.md)中的约定处理。注册和改资料时，昵称去除首尾空白后的长度为 1–50 个字符，允许重名。`PATCH /me/profile` 只接受 `displayName`，提交 `userId` 或其他字段返回 `400`。登录时，未知邮箱、密码错误和停用账号使用同一个 `401` 响应。

## 会话与错误约定

- 面向同源部署的 Vue 网页，使用带 `HttpOnly` 和 `SameSite=Lax` 的会话 Cookie，正式环境启用 HTTPS 和 `Secure`。
- 四个写接口均校验 CSRF Token。客户端先从辅助接口获取 Token，登录和退出后重新获取。CSRF 校验失败返回 `403 CSRF_INVALID`，写请求可能在认证检查前被拒绝。
- 退出只有在服务端完成当前会话撤销后才返回 `204`。若当前会话此前已失效，前端收到 `401` 后可清理本地登录状态。Redis 不可用时返回 `503`，前端显示退出尚未确认。
- 错误响应至少包含 `code` 和 `message`。账号密码、哈希、会话 ID 以及 CSRF Token 不写入业务日志。本人资料和 CSRF 响应使用 `Cache-Control: no-store`。
