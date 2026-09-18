# 部署与迁移：RabbitMQ -> Redis Streams + 内嵌认证任务网关

> 本文档只描述本地/预发可执行的步骤，**不代表生产已完成部署**。生产迁移必须由运维在维护窗口内人工执行。
> 远端 Nacos 上传、数据库迁移、节点与后端切换均由人工完成；本仓库只提供本地快照、增量 SQL 与示例。

## 1. 变更概览

- 判题任务分发：`RabbitMQ` 全部替换为 `Redis Streams`（固定 `default/spj/interactive` 三个 Stream key）。
- 任务网关：Go 节点改为通过后端 HTTPS 的 `/judge/tasks/**`、`/judge/submissions/**`、`/judge/nodes/**`、`/judge/problems/**` 接口交互，运行期只持有逐节点 Bearer JWT。
- 权威状态：`MySQL` 的 `judge_task_execution` 是任务所有权与执行资格的权威来源；Redis 只负责分发。
- 移除：`spring-boot-starter-amqp` 依赖、Rabbit 配置/凭证/Compose/脚本命令、共享正式主密钥（`judge_formal_token`）机制。

## 2. SQL 迁移（增量，不重写业务数据）

- 全新安装：直接执行 `deploy/mysql/hnieoj_多数据库.sql`（已包含新表/新列）。
- 存量升级：执行 `deploy/mysql/upgrade/20260918_redis_gateway.sql`。内容为纯增量：
  - `judge_node_token` 新增 `approved_max_concurrency`、`authorization_until`、`draining`；
  - `judge_task_outbox` 新增 `stream_key`、`stream_id`；
  - 新建 `judge_task_execution`（MySQL 权威执行租约），其中 `terminal_fingerprint` 保存终态业务内容的 SHA-256 指纹，用于同身份同 attempt 的终态幂等重报；重判/重派时清空。
- 恢复补偿的退避与预算复用 `judge_task_outbox.next_retry_time` / `retry_count`：
  - 恢复扫描在 `judge_task_execution` 行锁内**原子预占**一次补偿投递（`retry_count + 1` 并写入下次到期时间），
    再执行 `XADD`；即使 `XADD` 失败，退避与计数也已持久化，不会在下一个扫描周期紧凑重派。
  - 到期前扫描只跳过、不产生副作用；`retry_count` 达到 `max_retry_count` 时任务置 `SYSTEM_ERROR`，不会无限重试。
  - 该预算与正常首投的 Outbox 重试共用，真实领取执行次数（`attempt_count`）不受恢复补偿影响。
- 遗留列说明：
  - `judge_task_outbox.exchange_name` / `routing_key` 是 **NOT NULL 遗留列**。为了让旧库无需数据重写即可继续插入，代码会写入固定标记（`exchange_name='redis-streams'`，`routing_key=Stream key`），但**不参与任何路由**；真实分发定位使用 `stream_key` / `stream_id`。
  - `judge_formal_token` 表保留历史数据，**不再参与鉴权**，可后续另行清理。
- **存量任务不回填**：`judge_task_execution` 只对迁移后新提交/新重判的任务生效。旧系统已经投递到 RabbitMQ 或仍停留在 `judge`/`judge_task_outbox` 的非终态任务，不会被新 Redis 恢复流程自动接管。因此必须在切换前排空（见第 5 节），不能假设“旧任务处于新恢复范围内”。

执行顺序建议：先备份 -> 执行增量 SQL -> 校验列存在 -> 再切换服务。

## 3. Redis 要求

- 仅后端内网可达，**不要暴露给判题节点或公网**（`deploy/docker/docker-compose.redis.yml` 默认只绑定 `127.0.0.1`）。
- 必须开启持久化：`appendonly yes`，建议 `appendfsync everysec`。
- 内存策略必须为 `noeviction`，避免任务消息被淘汰。
- 建议配置监控与备份（RDB/AOF 快照），并关注 `XLEN`、`XPENDING`、`XINFO GROUPS`。
- 新实现不做 `MAXLEN` 近似裁剪（会误删仍在 PEL 中的在途消息），仅在任务终态/孤儿回收时用原子 `XACK + XDEL` 清理。

## 3.1 时区一致性前提（必须）

`judge_task_execution` 的租约/截止使用 epoch 毫秒（时区无关），但 `judge_task_outbox.next_retry_time`、
`judge_node_token.expire_time`、`gmt_modified` 等都是 `DATETIME`，由 Java 写入并与 MySQL 会话时间比较。
**部署必须保证 MySQL 全局时区、JDBC 会话时区与后端 JVM 时区一致**（本项目统一使用 `Asia/Shanghai` / `+08:00`）：

- MySQL：`SELECT @@global.time_zone, @@session.time_zone;` 应为 `+08:00`（或与 JVM 相同的命名时区）；
- JDBC：连接串显式 `serverTimezone=Asia/Shanghai`（部署快照已包含）；
- JVM：容器/主机时区与 MySQL 一致（如 `TZ=Asia/Shanghai`）。

若三者不一致，新写入的 `DATETIME` 会被读取为相差数小时，表现为新任务/新 outbox “看起来已过期”或“尚未到期”，
从而误判退避与租约。该问题属于**部署前置条件**，不是业务时间逻辑缺陷，禁止用源代码改写时间字段来掩盖。

## 4. Nacos 配置

- 本仓库仅维护本地快照 `deploy/nacos/dev/**`；**上传由用户/运维手动完成**。
- 需要同步的关键项：
  - `DEFAULT_GROUP/hnieoj-submission.yaml`：新增 `hnieoj.judge.stream.*` 与 `hnieoj.judge.security.temp-node-*`，移除 `spring.rabbitmq`、`hnieoj.judge.mq.*`、`hnieoj.judge.formal-token.*`。
  - `DEFAULT_GROUP/gateway.yaml`：判题节点路由新增 `/judge/tasks/**`。
  - `HNIEOJ_SECRET_GROUP/hnieoj-secrets.example.yaml`：移除 RabbitMQ 段与正式 Token 密文段。
  - `HNIEOJ_JUDGE_GROUP/hnieoj-judge-node.yaml`：**已删除**，不再导入 Nacos。判题节点**不连接 Nacos**，实际使用同级 go-judge 仓库的 `deploy/config.formal.example.yaml` / `deploy/config.temp.example.yaml` 生成的本地 `config.yaml` 与逐节点凭证文件。
- 节点凭证文件路径、临时授权码、节点名称等私有信息不要写入 Nacos。

## 5. 协同切换步骤（停机窗口）

切换前必须**排空旧链路**，且验证结果必须为零，否则阻止切换：

```sql
USE `hnieoj_judge_db`;

-- 1) 仍有非终态判题提交（PENDING/COMPILING/RUNNING）=> 不能切换
SELECT COUNT(*) AS unfinished_judge FROM `judge` WHERE `status` IS NULL OR `status` < 0;

-- 2) 仍有未投递完成的 Outbox => 不能切换
SELECT COUNT(*) AS unsent_outbox FROM `judge_task_outbox`
 WHERE `status` IN ('pending', 'processing', 'failed');

-- 3) 仍有未完成的批量重判任务 => 不能切换
SELECT COUNT(*) AS unfinished_rejudge FROM `rejudge_task`
 WHERE `status` IN ('pending', 'processing');
```

处理原则：
1. 先关闭提交入口（网关/前端停止产生新提交）并停止旧版 Go 消费者领取新任务；
2. 等待旧链路把在途任务与批量重判跑完，直到上面三条 SQL 全部返回 0；
3. 若长时间非零，**不要静默丢弃**：让旧 RabbitMQ 消费者继续消费至完成；确实无法完成的任务，由管理员在切换后通过面向用户的重判入口重新提交（生成新 `judgeTaskId`，走新管线），而不是直接把旧记录改写成新执行记录；
4. 确认归零后，按顺序执行：增量 SQL -> 上传更新后的 Nacos 快照并确认后端读取到新配置 -> 滚动发布后端八服务（确认 `/actuator/health` 正常）；
5. 发布重写后的 Go 节点（使用新的 `/judge/tasks/claim` + 续期 + 事件回传协议）；
6. 用 `POST /api/admin/judge/nodes/formal-tokens` 为正式节点签发独立凭证；临时节点用 `POST /api/judge/temp-token` 兑换；
7. 观察 Redis Streams 的 `XPENDING` 与 `judge_task_execution` 是否收敛；确认无长期 `SENT`/`PENDING` 卡死任务；
8. 确认稳定后，再考虑下线 RabbitMQ 基础设施（本仓库不再需要其配置/凭证）。

## 6. 回滚说明（不是“只换旧镜像”）

回滚并不只是恢复旧后端/旧节点镜像：

- 新 Redis 发布器写入的 Outbox 带有 `exchange_name='redis-streams'` 标记，且 `routing_key` 存的是 Stream key 而非旧 Rabbit 路由键；旧 Rabbit 发布链路**不会**自动消费这些未完成任务。
- `judge_task_execution` 中的未完成租约只由新恢复扫描处理；旧镜像没有该表逻辑。
- 旧 Go 节点依赖旧的共享正式主密钥与旧 Nacos 节点配置。

因此回滚必须：
1. 先按第 5 节排空并校验三条 SQL 全部为 0；非零时先在新系统上跑完，不做静默回滚；
2. 停止新后端与新 Go 节点，恢复旧后端与旧 Go 节点镜像；
3. 恢复旧 Nacos 节点配置与旧正式节点凭证机制（`hnieoj-judge.yaml`、`HNIEOJ_SECRET_GROUP` 相关 Data ID、共享主密钥文件），并同步后端 Nacos 快照；
4. 增量 SQL 为向后兼容的新增列/表，可保留，无需回滚业务数据；`judge_task_execution`、`stream_key`、`stream_id` 等新列在旧代码下不使用。
5. 若无法排空，禁止直接回滚，避免任务永久卡死或重复执行。

## 7. HTTPS 终止

- 生产必须由现有入口（网关/LB/Nginx）终止 HTTPS；Go 节点默认要求远程后端为 HTTPS，仅 `localhost` 允许 HTTP，且不跳过证书校验。
- 判题任务接口路径：`/judge/tasks/**`、`/judge/submissions/**`、`/judge/nodes/**`、`/judge/problems/**`。
- 本地 TLS 反向代理示例（占位证书路径，务必替换；不要提交真实私钥）：

```nginx
server {
    listen 8443 ssl;
    server_name oj.example.com;

    # 占位路径：请替换为真实的证书与私钥路径
    ssl_certificate     /etc/nginx/certs/oj.example.com.fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/oj.example.com.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    location /judge/ {
        proxy_pass http://127.0.0.1:8800;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    location / {
        proxy_pass http://127.0.0.1:8800;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

## 8. 验收相关

- 普通 `mvn test` 只跑单元测试，不依赖本机专属容器；真实集成测试使用本机 MySQL8/Redis7.4，通过显式 `-Dit.enabled=true` 运行：
  ```bash
  mvn -pl hnieoj-submission -am test -Dit.enabled=true
  ```
- 集成测试在建立上下文前自动创建/重建测试 schema `hnieoj_judge_it`（见 `hnieoj-submission/src/test/resources/sql/judge_it_schema.sql`），并只操作测试 schema 与测试 Redis DB，不触碰生产或预置库。
- 集成测试使用 `src/test/resources/application-test.yml` 的哑元基础设施配置并显式关闭 Nacos/bootstrap，不连接默认公共 Nacos 地址。

## 9. 部署对齐实现记录（2026-09-18）

范围：在后端 Redis Streams + 内嵌 HTTPS 网关实现冻结（候选 `redis-04`）之后，仅调整部署/配置/文档，使其与同级 go-judge 的实际 Go 实现一致；不改动生产 Java/SQL 代码，不新增依赖，不执行生产部署。

本阶段变更：

- `deploy/docker/docker-compose.gojudge.yml`：容器环境变量改为 Go 实际读取的 `HNIEOJ_BASE_URL` / `HNIEOJ_CREDENTIAL_TOKEN_FILE`；凭证改为挂载宿主 0700 的可写目录（容器内 `/etc/hnieoj/judge-node`），`config.yaml` 继续只读；移除沙箱对宿主机的 `5050` 发布端口；节点通过内网 `go-judge-sandbox:5050` 访问沙箱。
- `deploy/docker/.env.example`、`deploy/scripts/deploy-dev.sh`：对齐 go-judge 变量名、移除公开端口选项，`gojudge-up` 会创建 0700 凭证目录。
- `deploy/nacos/**`：删除 `HNIEOJ_JUDGE_GROUP/hnieoj-judge-node.yaml` 快照及 `.metadata.yml` 引用；判题节点不连接 Nacos，示例改为同级 go-judge 的 `deploy/config.formal.example.yaml` / `deploy/config.temp.example.yaml`。
- `README.md`、`deploy/docker/README.md`、`deploy/MIGRATION-redis-gateway.md`：移除不符合 Go 实现的 `gateway.*` 节点配置、Nacos 节点分组与陈旧字段；补充正式/临时节点凭证、排空与管理员恢复接单说明。

已由 Codex 独立完成的验证（本阶段未重跑，也未做任何生产部署）：

- `redis-verify-04`：`mvn -B -Dit.enabled=true package` 全部通过（含 26 个真实数据库集成测试）。
- `redis-static-04`：升级/全新 schema 等价、8 服务/路由、Compose 与 shell 语法、canonical-null 探测通过。
- `redis-http-04`：两提交实例真实额度与终态重放/冲突/过期重放通过。
- `redis-security-http-04`：鉴权边界、任务数据下载、draining/撤销通过。
- `redis-temp-http-04-v2`：并发一次性注册、稳定有界续期、心跳不提升权限通过。
- `redis-outage-04`：实际停止/重启 Redis 容器，outbox 首次投递失败可存活、终态提交 ACK 失败可存活、恢复后重放通过。

Go/跨语言证据将在 Go 最终验证后由 Codex 另行提供，本记录不预判其结论。
