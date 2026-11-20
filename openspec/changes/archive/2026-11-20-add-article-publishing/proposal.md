## Why

创作者已经可以保存私有草稿，登录读者还无法阅读这些内容。先完成作者发布和按 ID 阅读，再接入基于关注关系的 Feed 分发。

## What Changes

- 作者可发布自己已保存的草稿。标题、正文均须包含非空白字符，提交版本必须匹配。发布沿用内容 ID 并记录首次发布时间。
- 登录用户可按 ID 读取已发布文章。作者用首次发布的请求版本重试时，返回已有发布结果。
- 首期交付发布和阅读 API。Feed、发布事件、匿名阅读、已发布文章编辑和下架留到后续。

## Capabilities

### New Capabilities

- `article-publishing` 覆盖发布、同版本重试、已发布文章的读取权限和本阶段的 CDC 边界。

### Modified Capabilities

无。

## Impact

- 扩展 Java 内容模块及 MySQL `content` 表，复用身份模块的会话与 CSRF，以及现有草稿的作者与版本规则。
- 新增发布和阅读接口，当前草稿 API 继续按 `DRAFT` 状态过滤。
- 沿用现有 Canal 过滤配置，发布阶段的成功响应只依赖 MySQL 提交。范围见[内容设计](../../../docs/design/content/design.md#文章发布与阅读)。
