# CreatorHub

## 本地 Web 前端

先按下文启动 Java API，然后在项目根目录执行。

```sh
npm --prefix frontend ci
npm --prefix frontend run dev
```

打开 [CreatorHub 本地前端](http://127.0.0.1:5173/)。注册并登录后，可以在「我的草稿」新建、查看和手动保存 Markdown 草稿，也可以在「账号设置」修改昵称。开发服务器将 `/api` 转发到 `http://127.0.0.1:8080`，浏览器在同源下使用会话 Cookie。API 端口改动时，启动前端前可设置 `CREATORHUB_API_TARGET`。

```sh
npm --prefix frontend run build
npm --prefix frontend run preview
```

构建产物在 `frontend/dist/`。部署静态文件时，让同一域名下的 `/api` 转发到 Java API。

## 本地身份 API

身份模块的接口见 [API 设计](docs/design/identity/api.md)。需要 Java 21、Maven 和下文的 Docker MySQL、Redis。从项目根目录执行。

```sh
test -f .env || cp .env.example .env
docker compose up -d --build --wait
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"  # macOS
set -a
. ./.env
set +a
export CREATORHUB_DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT:-3306}/creatorhub?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
export CREATORHUB_REDIS_PORT="${REDIS_PORT:-6379}"
mvn -f backend/pom.xml clean package
java -jar backend/creatorhub-api/target/creatorhub-api-0.1.0-SNAPSHOT.jar
```

Flyway 在 API 启动时执行迁移。全新数据库依次执行 V1 至 V6。升级已有环境时，先通过上面的 Compose 命令应用 Canal 白名单，再启动含 V6 的 API。若现有本地数据库只包含已知的 `cdc_smoke_probe` 验证表且尚无 Flyway 历史，首启使用下面这条命令登记版本 0 并执行迁移，原有验证表会保留。

```sh
CREATORHUB_DB_BASELINE_ON_MIGRATE=true java -jar backend/creatorhub-api/target/creatorhub-api-0.1.0-SNAPSHOT.jar
```

要运行两个实例，在另一个终端加载相同的环境变量后，使用不同的端口。两个实例连接同一个 MySQL 和 Redis，并使用相同的会话命名空间。

```sh
CREATORHUB_PORT=8081 java -jar backend/creatorhub-api/target/creatorhub-api-0.1.0-SNAPSHOT.jar
```

默认绑定到 `127.0.0.1:8080`。Redis 会话闲置超时为 30 分钟，可通过 `CREATORHUB_SESSION_TIMEOUT` 调整。通过 HTTPS 部署时设置 `CREATORHUB_SECURE_COOKIE=true`。密码使用 Argon2id，参数为 19 MiB 内存、2 次迭代、并行度 1、16 字节盐及 32 字节输出。当前版本的邮箱注册用于本地演示，账号注册后直接可用。

构建后可运行端到端验收。脚本创建独立的 MySQL、Redis 和两个 Java 进程，结束后清理这些测试进程和容器。Redis 故障实验只停止测试 Redis。

```sh
python3 docker/verify-identity.py
```

验收覆盖空库迁移、邮箱并发注册与事务回滚、跨实例会话与退出、会话过期、CSRF、昵称隔离、MySQL 索引和 Redis 故障响应。本次本地样本为 24 次登录、4 并发，P95 为 87.6 ms，计时从提交登录请求开始。

## 本地 Docker 环境

启动 Docker Desktop，然后在项目根目录执行：

```sh
test -f .env || cp .env.example .env
docker compose up -d --build --wait
docker compose ps -a
python3 docker/smoke-cdc.py
```

Compose 构建包含 CDC 配置和账号初始化脚本的 MySQL 开发镜像，并启动
MySQL、Redis、Kafka 和 Canal。Kafka 初始化容器创建
`creatorhub.cdc` 后正常退出，Canal 等 MySQL 可用且 Topic 创建完成后启动。
验证脚本从 Kafka 读取测试记录的 INSERT、UPDATE 和 DELETE 事件。
测试表保留用于下次验证，每次验证产生的测试行会删除。

| 服务 | 固定版本 | 本机开发连接 | 容器间连接 |
| --- | --- | --- | --- |
| MySQL | 8.4.13 | `127.0.0.1:3306/creatorhub` | `mysql:3306` |
| Redis | 8.2.11 | `127.0.0.1:6379` | `redis:6379` |
| Kafka（KRaft） | 4.3.2 | `127.0.0.1:9092` | `kafka:29092` |
| Canal | 1.1.8 | 容器内部运行 | 订阅 MySQL 并写入 Kafka |

Java 连接 MySQL 时使用 `creatorhub` 应用账号和 `.env` 中的
`MYSQL_APP_PASSWORD`。Canal 使用独立账号读取 Binlog。本机端口可通过
`.env` 调整。默认密码及 Redis、Kafka 的连接配置仅用于本地开发。
Canal 密码支持字母、数字、下划线及连字符。

当前 CDC 使用单分区原始变更 Topic，消息格式为 Canal Flat JSON。Canal
白名单仅包含验证用表 `creatorhub.cdc_smoke_probe`。私有草稿和关注关系保存在 MySQL，业务表的采集范围随分发设计确认。
Kafka 使用单节点及单副本，MySQL Binlog 保留七天。

MySQL、Redis、Kafka 和 Canal 位点各使用命名卷。MySQL 初始化账号的脚本
只在数据库卷首次创建时运行。已有卷上的账号密码须先在数据库内修改，
再同步更新 `.env` 和重建相关容器。

```sh
docker compose logs --tail=100 mysql kafka kafka-init canal
docker compose exec -T canal tail -n 100 /home/admin/canal-server/logs/creatorhub/creatorhub.log
docker compose down
```

`docker compose down` 关闭环境并保留命名卷。加上 `-v` 会删除所有本地数据及 CDC 位点。
