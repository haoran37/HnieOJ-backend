# HNieOJ 从零部署检查清单

这份清单用于服务器清空后重新部署，目标是确认 8 个后端服务、Redis 与判题节点能完整协同工作。

## 1. 前置服务

部署后端容器前，先准备：

- MySQL 8.0
- Redis
- Nacos 2.3.2
- Docker 与 Docker Compose 插件
- Git、Maven、OpenSSL

HNieOJ 二开 go-judge 判题节点可以通过 `deploy/scripts/deploy-dev.sh` 启动；判题任务分发使用共享 Redis Streams，不再需要 RabbitMQ。

## 2. 数据库

清库重建时执行：

```bash
mysql -uroot -p < deploy/mysql/hnieoj_多数据库.sql
```

需要开发测试数据时再执行：

```bash
mysql -uroot -p < deploy/mysql/添加测试数据.sql
```

安装与重建统一执行完整初始化脚本 `deploy/mysql/hnieoj_多数据库.sql`；执行会重建表，请确认目标库。

已有数据库升级到当前 `dev` 时，不要重跑会删表的初始化脚本。先备份数据库，再分别执行增量迁移：

```bash
mysql -uroot -p hnieoj_user_db < deploy/mysql/migrations/20260923_invite_code.sql
mysql -uroot -p hnieoj_user_db < deploy/mysql/migrations/20260923_user_favorite.sql
mysql -uroot -p hnieoj_judge_db < deploy/mysql/migrations/20260923_judge_homework_index.sql
```

邀请码和收藏接口依赖两张新表，作业成绩单依赖新增的判题表索引；后端部署脚本不会自动执行数据库迁移。

## 3. Nacos

导入这些配置：

- `deploy/nacos/dev/DEFAULT_GROUP/*.yaml`（8 个可执行服务对应的配置）

按示例创建这些 secret group 配置：

- `HNIEOJ_SECRET_GROUP/hnieoj-secrets.yaml`

规则：

- 不要导入 `HNIEOJ_JUDGE_GROUP/hnieoj-judge-node.yaml`：判题节点 Agent 不再读取 Nacos，节点运行参数只来自本机 `config.yaml` 与环境变量。
- `hnieoj-secrets.yaml` 只保留环境变量占位符。
- 不要把真实 MySQL、Redis、内部 token、判题 JWT secret 或 `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET` 写入 Nacos。
- 共享正式节点 token 密文不再下发到 Nacos；节点身份统一走 Bootstrap + Ed25519 注册。

## 4. 环境变量文件

部署脚本使用：

```text
/opt/hnieoj/backend/.env
```

如果该文件不存在，先执行：

```bash
bash deploy/scripts/deploy-dev.sh
```

脚本会生成模板并停止。至少需要填写：

- `NACOS_SERVER_ADDR`
- `NACOS_NAMESPACE`
- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`

脚本可以自动生成或补齐：

- `HNIEOJ_INTERNAL_TOKEN`
- `HNIEOJ_JUDGE_JWT_SECRET`
- `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET`

只想生成安全材料时，可以单独执行：

```bash
bash deploy/scripts/deploy-dev.sh security-init
```

## 5. 部署顺序

推荐顺序：

```bash
bash deploy/scripts/deploy-dev.sh deploy
bash deploy/scripts/deploy-dev.sh gojudge-up
```

`gojudge-up` 会按“显式环境变量 > `.env` > 默认值”解析状态/配置/Bootstrap/缓存宿主路径，并预置只读配置 bind 的宿主目标 `${HNIEOJ_JUDGE_STATE_HOST_DIR}/config.yaml`（缺失时创建 0600 空文件，绝不截断既有内容）。直连 Compose 时需先手工预置，否则 Docker 会把嵌套的 `config.yaml` 建为目录导致节点启动失败：

```bash
sudo mkdir -p /data/oj/judge-node /etc/hnieoj/judge-node /data/oj/judge-cache
sudo chmod 700 /data/oj/judge-node /etc/hnieoj/judge-node
# 仅在缺失时创建 0600 空占位，绝不截断既有 config.yaml 或 identity.json。
[[ -f /data/oj/judge-node/config.yaml ]] || { sudo touch /data/oj/judge-node/config.yaml && sudo chmod 600 /data/oj/judge-node/config.yaml; }
```

节点 `config.yaml` 的 `hnieoj.baseUrl`、`hnieoj.wssUrl` 必须分别为 HTTPS/WSS，`hnieoj.audience` 必须等于后端 `HNIEOJ_JUDGE_NODE_AUDIENCE`（当前默认 `hnieoj-judge-node`）；三者需与后端入口一起修改，否则 WSS 认证失败。

查看容器状态：

```bash
bash deploy/scripts/deploy-dev.sh ps
bash deploy/scripts/deploy-dev.sh gojudge-ps
```

查看日志：

```bash
bash deploy/scripts/deploy-dev.sh logs hnieoj-gateway
bash deploy/scripts/deploy-dev.sh logs hnieoj-submission
bash deploy/scripts/deploy-dev.sh gojudge-logs
```

## 6. 判题链路验证

后端和判题节点启动后，按顺序验证：

1. 打开 gateway health 接口。
2. 使用教师或管理员账号登录。
3. 上传或确认一个包含测试数据的题目。
4. 提交一份应当 Accepted 的简单代码。
5. 确认提交状态能离开 `Pending`。
6. 确认 judge-node 日志出现任务接收与结果回传。
7. 确认 WebSocket 或轮询接口能看到最终状态。

运维接口：

```http
GET /api/admin/submissions/judge-ops/summary
GET /api/admin/submissions/judge-outbox
GET /api/admin/judge/nodes
GET /api/admin/judge/nodes/summary
```

Redis Streams / 租约检查：

```bash
# <redis-container> 请替换为实际部署的 Redis 容器名（或直接用 redis-cli -h/-p 连接）。
# <stream-key> 取 hnieoj.judge.stream.default-stream-key / spj-stream-key / interactive-stream-key
# （默认分别为 hnieoj:judge:task:default / hnieoj:judge:task:spj / hnieoj:judge:task:interactive）；
# <group> 取 hnieoj.judge.stream.consumer-group（默认 hnieoj-judge-gateway）。
docker exec -it <redis-container> redis-cli XINFO GROUPS <stream-key>
docker exec -it <redis-container> redis-cli XPENDING <stream-key> <group>
```

任务已在数据库终态提交后回 `TASK_RESULT_ACK`；不要手工删除 PEL 中在途条目。

## 7. 上线前安全检查

对外开放前确认：

- 只对公网暴露 gateway 端口。
- 服务端口、MySQL、Redis、Nacos 都应仅内网访问或受防火墙保护。
- 节点 WSS `/ws/judge/node` 与签名测试数据 HTTP 只经 TLS 入口暴露；反代需足够的握手/空闲超时（建议 ≥ 300s）并透传 `Upgrade`/`Connection`。
- `/internal/**` 不得暴露到公网。
- `/etc/hnieoj/judge-security` 只允许可信部署用户读取。
- 判题节点状态目录（默认 `/data/oj/judge-node`，0700）与一次性 Bootstrap 目录（默认 `/etc/hnieoj/judge-node`，0700；目录内 `bootstrap.token` 0600）只允许可信部署用户访问；节点 Ed25519 私钥只保存在状态目录的 `identity.json`，绝不挂入沙箱。
- `.env` 权限保持为 `600`。
- 不要打印判题任务 payload，因为消息中包含用户源码。
- `HNIEOJ_JUDGE_JWT_SECRET`、`HNIEOJ_INTERNAL_TOKEN` 与 `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET` 不是占位值。

## 8. 常用恢复命令

重启单个服务：

```bash
bash deploy/scripts/deploy-dev.sh restart hnieoj-submission
```

重启全部后端服务：

```bash
bash deploy/scripts/deploy-dev.sh restart
```

查看 go-judge 缓存：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-cache-status
```

清理 go-judge 题目缓存：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-cache-clean 7
```

停止可选组件：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-down
```

## 9. 节点身份协议 v1 部署边界（本地/预发验证，非生产验收）

节点身份 v1 引入 Ed25519 注册、短期 NODE_ACCESS 令牌与 WSS 节点通道。以下边界为本地或预发
验证所需，不构成生产 DNS/证书/防火墙或数据迁移验收结论；本仓库未在生产环境执行或验证。

### 9.1 Redis 持久化与恢复

- 判题任务以固定 Stream key + MySQL 权威租约为准，Redis 只是传递与 PEL 载体。
- 建议 Redis 开启 AOF（`appendonly yes`）并设置 `maxmemory-policy noeviction`，避免在途判题任务被内存回收静默驱逐；使用外部托管 Redis 时请在控制台确认等效策略。
- 恢复：Redis 重启/丢数据后，后端恢复扫描会依据 MySQL 中滞留 `queued` 与过期租约重新补偿派发；
  不要手工删除 PEL 中在途条目，先核对 `hnieoj.judge.stream.*` 配置与消费组再观察补偿结果。

### 9.2 节点重新注册（维护窗口）

- 存量节点需在维护窗口内通过管理员一次性 Bootstrap 按 v1 协议重新注册（详见 `docs/judge-ops.md`）；
  不要在未确认切换前并行保留旧 bearer-only 通路。
- 所有后端副本必须使用同一 `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET`（跨实例签发的 NODE_ACCESS
  令牌需可互认），且 `HNIEOJ_JUDGE_NODE_AUDIENCE` 保持一致；两者只允许运行时环境/文件注入，
  不得写入普通 Nacos 配置。
- 密钥缺失、非正 TTL、越界帧大小等非法配置启动即失败（fail-fast）。

### 9.3 通道与回滚边界

- WebSocket：`/ws/judge/node` 需经 TLS 终结（入口终止 TLS），普通文本回环仅限本地测试；
  服务自身提供认证截止（默认 10s）、64KiB 控制帧 / 4MiB 任务帧上限、时间戳偏差与有界串行写出。
- 反代/网关：WebSocket 需配置足够的空闲/握手超时；`/internal/**` 不得暴露到公网；
  `/judge/nodes/**` 与 `/ws/judge/node` 在网关为 public 路径，由协议层自身鉴权。
- 回滚：回滚前确认没有节点已切换到 v1 认证；v1 表/列保留，不做破坏性回滚。

管理端节点状态操作（`drain` / `enable` / `disable` / `policy` / `revoke`）需要 `ADMIN` 或 `ROOT`
权限，路径与语义见 `docs/judge-ops.md`。
