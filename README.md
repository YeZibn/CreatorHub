# CreatorHub

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
白名单包含 `creatorhub.content`、`creatorhub.follow` 和验证用表
`creatorhub.cdc_smoke_probe`。业务表名、Topic 路由和分区规则随模块设计确认。
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
