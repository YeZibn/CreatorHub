# User Identity Specification

## Purpose

为 CreatorHub 的个人账号提供注册、登录、退出和昵称修改，并为后续的内容归属和个人 Feed 提供可信的服务端用户身份。

## Requirements

### Requirement: 注册个人账号

`POST /api/v1/auth/register` SHALL 接受 `email`、`password` 和 `displayName`，在同一个事务内创建账号与资料。两者 SHALL 使用同一个用户 ID。同一规范化邮箱 SHALL 只有一个账号。邮箱使用 ASCII，去除首尾空白并转换为小写。密码 SHALL 以哈希存储。昵称去除首尾空白后 SHALL 为 1 至 50 个字符，允许重名。

#### Scenario: 新账号注册成功
- **WHEN** 提交有效资料和未注册的邮箱
- **THEN** 系统提交账号及资料并返回 `201`，响应中的 `userId` 为字符串，用户可单独登录

#### Scenario: 并发注册同一邮箱
- **WHEN** 两个携带有效 CSRF Token 的请求并发提交仅在大小写或首尾空白上不同的同一邮箱
- **THEN** 只有一个请求返回 `201`，另一个返回 `409 EMAIL_ALREADY_REGISTERED`，最终只有一条账号记录与一条关联资料记录

#### Scenario: 资料写入失败
- **WHEN** 注册事务内的账号插入成功而资料插入失败
- **THEN** 系统回滚账号插入并返回失败

#### Scenario: 请求字段无效
- **WHEN** 提交无效邮箱、密码或昵称
- **THEN** 返回 `400 INVALID_REQUEST`，数据库中不创建账号或资料

### Requirement: 登录与当前身份查询

`POST /api/v1/auth/login` SHALL 用规范化邮箱和密码认证处于 `ACTIVE` 状态的账号。成功后 SHALL 创建会话或轮换已有会话 ID，浏览器通过 Cookie 持有会话 ID。不同 API 实例 SHALL 识别同一个有效会话。`GET /api/v1/me` SHALL 根据会话返回本人的 `userId`、`email` 和 `displayName`。

#### Scenario: 登录成功后跨实例读取身份
- **WHEN** 用户在 API 实例一成功登录，并携带颁发的 Cookie 向实例二请求 `/me`
- **THEN** 登录返回 `200`、`userId` 和 `displayName`，实例二返回该用户本人的身份，回传的 `userId` 为字符串

#### Scenario: 登录前的会话 ID 不携带登录身份
- **WHEN** 客户端在登录成功后以登录前的 Cookie 请求 `/me`
- **THEN** 返回 `401 UNAUTHENTICATED`

#### Scenario: 登录凭证不可用
- **WHEN** 邮箱不存在、密码不匹配或账号已停用
- **THEN** 返回同样的 `401 INVALID_CREDENTIALS`，请求不会获得认证会话

#### Scenario: 读取当前用户时会话失效
- **WHEN** 请求 `/me` 时 Cookie 缺失或会话已过期
- **THEN** 返回 `401 UNAUTHENTICATED`

### Requirement: 退出当前会话

`POST /api/v1/auth/logout` SHALL 先撤销当前会话，再清理会话 Cookie 并返回 `204`。同账号的其他独立会话 SHALL 继续可用。

#### Scenario: 旧 Cookie 在所有 API 实例上失效
- **WHEN** 用户成功退出后，新请求携带退出前的 Cookie 访问任一 API 实例的 `/me`
- **THEN** 返回 `401 UNAUTHENTICATED`，同账号另外登录的设备仍可访问 `/me`

#### Scenario: 退出前会话已失效
- **WHEN** 退出请求通过 CSRF 校验，但当前会话已失效
- **THEN** 返回 `401 UNAUTHENTICATED`

### Requirement: 修改本人昵称

`PATCH /api/v1/me/profile` SHALL 只接受 `displayName`，更新对象 SHALL 由服务端认证身份确定。昵称去除首尾空白后 SHALL 为 1 至 50 个字符，允许重名。更新提交后 SHALL 返回 `200` 和本人的 `userId`、`displayName`。

#### Scenario: 修改自己的昵称
- **WHEN** 用户 A 携带有效会话和 CSRF Token 提交有效昵称
- **THEN** 只更新 A 的资料，随后查询 `/me` 可看到新昵称

#### Scenario: 请求试图指定其他用户
- **WHEN** 用户 A 将 B 的 `userId` 或任意未声明字段放入修改请求体
- **THEN** 返回 `400 INVALID_REQUEST`，两人的资料均无改动

### Requirement: 浏览器请求保护与失败响应

`GET /api/v1/auth/csrf` SHALL 提供 `headerName` 和 `token`。注册、登录、退出和资料修改 SHALL 校验 CSRF Token。会话 Cookie SHALL 设置 `HttpOnly`、`SameSite=Lax`，在 HTTPS 正式环境设置 `Secure`。本人资料及 CSRF 响应 SHALL 使用 `Cache-Control: no-store`。错误响应 SHALL 包含 `code` 和 `message`。

#### Scenario: 写请求的 CSRF Token 缺失或无效
- **WHEN** 请求四个写接口之一时缺少有效的 CSRF Token
- **THEN** 返回 `403 CSRF_INVALID`，业务状态不发生变化

#### Scenario: 会话存储不可用
- **WHEN** 登录或退出无法完成会话写入或撤销，或者读取当前身份或 CSRF Token 时所需的会话存储不可用
- **THEN** 返回 `503 SESSION_UNAVAILABLE`，不报告操作成功
