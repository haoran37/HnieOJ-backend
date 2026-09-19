# HNieOJ 判题链路运维手册

## 范围

本文记录 HNieOJ 当前判题链路的运维检查方式：

```text
submission -> judge_task_outbox -> Redis Streams -> hnieoj-judge-node(WSS) -> result callback
```

## 后端检查

### 判题链路摘要

```http
GET /api/admin/submissions/judge-ops/summary
```

权限：`problem:update`。

返回内容包括：

- outbox 各状态数量：`pending`、`processing`、`sent`、`failed`、`exhausted`
- 可重试 outbox 数量
- 异常 outbox 数量
- 正在判题的提交数量：`Pending`、`Compiling`、`Running`
- 超时 Pending 提交数量
- 超时活跃提交数量
- 长时间 Pending 告警数量
- dashboard 可直接展示的 warning code

建议 dashboard 规则：

- `healthy = true`：暂无需要立即处理的问题。
- `outbox_exhausted`：检查 outbox 详情，修复 Redis Streams 连接后手动重试。
- `outbox_abnormal`：检查 Redis 连通性、固定 Stream key、消费组与 XADD 日志。
- `stale_pending_submission`：检查队列积压和 judge-node consumer。
- `stale_active_submission`：检查 judge-node 执行和回调日志。
- `long_pending_submission`：仅作告警，不要在未确认队列积压前直接标记失败。

### Outbox 详情

```http
GET /api/admin/submissions/judge-outbox
POST /api/admin/submissions/judge-outbox/{id}/retry
```

当摘要中出现 `failed` 或 `exhausted` outbox 记录时，使用这些接口查看详情并重试。

## Redis Streams / 租约检查

判题任务不再经过 RabbitMQ，统一走固定 Stream key 与 MySQL 权威租约：

- Stream key 只能取 `hnieoj.judge.stream.default-stream-key` / `spj-stream-key` / `interactive-stream-key`，
  不接受外部任意指定。
- 每个 submission 实例只调度本实例持有的 READY 节点连接；额度与所有权由 `judge_task_execution`
  行锁判定，Redis 中断时 Outbox 与恢复扫描仍会补偿。
- `judge_task_execution` 的 `lease_until` 为 Unix 毫秒，且被 `execution_deadline`、
  节点 `authorization_until` 与 `expire_time` 封顶；硬截止到期不可续租。
- `status=queued` 表示等待领取；`leased/running` 且租约未过期表示在途；`completed/failed` 为终态。
- 恢复扫描会回收过期租约与滞留 queued，超过 `max_attempt_count` 的提交置 `SYSTEM_ERROR`。

无需再检查 RabbitMQ 队列/死信；如使用旧版部署脚本中残留的 `rabbitmq-*` / `judge-dlq-*`
命令，请改为检查 Redis Stream 长度与 PEL：

```bash
# <redis-container> 替换为实际 Redis 容器名（或直接用 redis-cli -h/-p 连接）。
docker exec -it <redis-container> redis-cli XINFO GROUPS <stream-key>
docker exec -it <redis-container> redis-cli XPENDING <stream-key> <group>
```

`<stream-key>` 只能取 `hnieoj.judge.stream.default-stream-key` / `spj-stream-key` /
`interactive-stream-key`（默认 `hnieoj:judge:task:default` / `hnieoj:judge:task:spj` /
`hnieoj:judge:task:interactive`），不接受外部任意指定；`<group>` 取
`hnieoj.judge.stream.consumer-group`（默认 `hnieoj-judge-gateway`）。建议 Redis 开启 AOF
并设置 `maxmemory-policy noeviction`，Redis 中断后由恢复扫描依据 MySQL 权威租约补偿。

终态结果先提交数据库再回 `TASK_RESULT_ACK`；回包丢失时节点重发相同内容即可幂等对账，
不同内容会被拒绝。LEASED 条目保留在 PEL，终态后才原子 `XACK + XDEL`。

## 判题节点检查

```http
GET /api/admin/judge/nodes
GET /api/admin/judge/nodes/summary
```

节点列表重点检查：

- `online`
- `lastHeartbeatTime`
- `runningTasks`
- `maxConcurrency`
- `supportedJudgeModes`
- `cacheUsedBytes`
- `cacheProblemCount`
- `diskFreeBytes`

如果启用 SPJ 或交互题，只能把任务投递给 `supportedJudgeModes` 包含对应模式的节点。

## 已退休的旧运行通路与映射

以下旧通路已在本轮最终切换中删除或显式拒绝，不再作为可用备用协议：

| 旧通路 | 现状 | 替代 |
| --- | --- | --- |
| `POST /api/judge/temp-token` 临时 bearer 令牌兑换 | 显式拒绝（403） | Bootstrap + Ed25519 注册 |
| `/internal/judge/tokens/validate` bearer/共享 formalToken 校验 | 恒返回无效 | `/internal/judge/task-access/validate` 签名任务访问校验 |
| 共享 formalToken / Nacos 下发正式密钥 | 初始化器删除，`matches` 恒 false，轮换显式拒绝 | 注册 + 密钥轮换 |
| 旧 body/IP 指纹绑定 | 仅作审计字段 | Ed25519 公钥身份 |
| HTTPS claim/renew 轮询 | 无对应实现 | WSS `RESUME_TASKS` / `LEASE_RENEW` |
| RabbitMQ 判题分发 | 依赖、Compose 服务与有效配置已移除 | Redis Streams + MySQL 租约 |

节点摘要接口（`GET /api/admin/judge/nodes/summary`）仍返回在线/离线、正式/临时、运行任务数、
并发容量、按 mode 的在线节点、过载/低磁盘/即将过期等聚合指标；`ADMIN`/`ROOT` 权限保持不变。

### 节点生命周期操作（ADMIN/ROOT）

管理端节点状态接口统一要求 `ADMIN` 或 `ROOT` 角色（`@SaCheckRole(ADMIN, ROOT)`）：

```text
POST /api/admin/judge/nodes/tokens/{tokenId}/drain     优雅排空：停止新调度，保留在途续租/结果
POST /api/admin/judge/nodes/tokens/{tokenId}/enable    恢复 active：通知节点清除远端排空，重新可调度
POST /api/admin/judge/nodes/tokens/{tokenId}/disable   禁用：推 NODE_STATE 并按 accessVersion 失效旧令牌
POST /api/admin/judge/nodes/tokens/{tokenId}/policy    调整并发/权重/支持判题模式等策略
POST /api/admin/judge/nodes/tokens/{tokenId}/revoke    吊销：紧急失效，旧 NODE_ACCESS 立即作废
GET  /api/admin/judge/nodes/tokens                     节点令牌列表
POST /api/admin/judge/nodes/bootstrap-tokens           为旧节点签发一次性 Bootstrap 重新入网
```

`drain`/`enable` 写入 DB 权威状态后由各后端实例的 WSS 连接收敛：入站消息（HEARTBEAT/READY 等）
与后台巡检共用同一去重通知逻辑，在 drain/enable 翻转时各推送一次 `NODE_STATE`，不依赖客户端
先发 heartbeat/READY。`drain` 只停止新调度，节点本地 SIGTERM 排空与在途任务不受影响；`enable`
推送 `active` 使节点清除远端排空标记。

## 部署注意事项

- 判题 JWT secret 与 `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET` 使用环境变量注入，不写入 Nacos。
- 节点私钥只保存在判题节点宿主机；服务器永不保存私钥、Bootstrap 明文或 accessToken 明文。
- 节点本机 `config.yaml` 的 `hnieoj.audience` 必须显式设为后端 `hnieoj.submission.judge.node-security.audience`（环境变量 `HNIEOJ_JUDGE_NODE_AUDIENCE`，当前默认 `hnieoj-judge-node`）；`baseUrl`（HTTPS）、`wssUrl`（WSS）与 `audience` 需与后端入口一起修改，留空或写错会导致 WSS 认证失败。
- 一次性 Bootstrap 以整目录只读挂载（目录 0700，`bootstrap.token` 0600），挂载点与示例 `bootstrap.tokenFile` 一致；服务端只原子消费 DB 中的 Bootstrap 记录，节点 Agent 会尝试删除本地明文并在只读挂载失败时告警，注册成功后由运维删除宿主机明文，已注册节点重启复用持久化 `identity.json`，无需再次 Bootstrap。
- 不要向公网暴露内部服务端口；WSS/HTTPS 只在私网或经受控反代入口暴露。

## 节点身份协议 v1（Ed25519）

节点身份协议 v1 以 Ed25519 公钥为唯一身份根，短期 NODE_ACCESS 令牌只用于业务鉴权，
数据库 `judge_node_token` 保存节点注册事实与固定截止时间，`judge_node_key` 保存公钥历史与
轮换状态。设备指纹与来源 IP 只作为审计数据，不再作为认证绑定条件。

### 端点

```text
POST /api/admin/judge/nodes/bootstrap-tokens             管理员创建一次性 Bootstrap（仅返回一次明文）
POST /judge/nodes/enrollment-challenges                  申请注册挑战（nonce 30s，Redis 单次消费）
POST /judge/nodes/enroll                                 提交注册证明，原子消费 Bootstrap
WSS  /ws/judge/node                                      节点实时通道（AUTH_CHALLENGE/RESPONSE/OK/REFRESH、HEARTBEAT、RESUME_TASKS）
POST /judge/nodes/keys/rotations                         密钥轮换 prepare（旧 ACTIVE 密钥授权）
POST /judge/nodes/keys/rotations/{rotationId}/confirm    密钥轮换 confirm（新密钥签名）
GET  /judge/nodes/keys/rotations/{rotationId}            轮换状态查询
GET  /judge/problems/{id}/testdata                       签名测试数据下载（Bearer NODE_ACCESS + Ed25519 原始 method/path/body 签名）
```

WSS 任务流：认证成功后先 `RESUME_TASKS`，再 `READY {availableSlots}` 允许调度；
`TASK_ASSIGN` 在 DB 提交租约后下发，`TASK_ACK` 只确认接收，`TASK_RUNNING` 回
`TASK_EVENT_ACK`，`TASK_RESULT` 在数据库终态提交后回 `TASK_RESULT_ACK`；`LEASE_RENEW`
受硬截止封顶并回 `LEASE_RENEWED`。REVOKE/DISABLE/硬到期会推送 `TASK_CANCEL` / `NODE_STATE`。

签名测试数据下载要求 Bearer `NODE_ACCESS` 与头
`X-Judge-Node-Id` / `X-Judge-Key-Id` / `X-Judge-Timestamp(epochms)` / `X-Judge-Nonce` /
`X-Judge-Signature`；canonical 字段为
`["HNIEOJ-HTTP-V1",audience,nodeId,keyId,uppercaseMethod,pathWithRawQuery,lowercaseBodySha256,timestamp,nonce]`。
problem 服务原样透传原始 method/path/body 摘要给 submission，由 submission 在同一事务内校验
令牌绑定、签名 nonce 单次消费、node/key/epoch 与 tasklease/problem 绑定；304 与错误路径同样先鉴权。

签名采用长度前缀规范编码（UTF-8 字段 + 4 字节大端长度），域分隔常量分别为
`HNIEOJ-ENROLL-V1`、`HNIEOJ-AUTH-V1`、`HNIEOJ-HTTP-V1`、`HNIEOJ-ROTATE-PREPARE-V1`、
`HNIEOJ-ROTATE-CONFIRM-V1`。已提交源码内公开向量位于
`common/src/test/resources/protocol-vectors.json`，由 `NodeSignatureCodecVectorTest`
逐字节校验，未引用任何仓库外冻结输入。

### 配置迁移映射

旧的 bearer-only 临时令牌参数不再恢复；对应能力由 `hnieoj.judge.node-security.*` 承载：

| 旧参数（将退休） | 新参数 | 说明 |
| --- | --- | --- |
| `HNIEOJ_JUDGE_TEMP_TOKEN_ALLOWED_CLOCK_SKEW_SECONDS` | `HNIEOJ_JUDGE_NODE_ALLOWED_CLOCK_SKEW_SECONDS`（默认 30） | 入站消息时间戳偏差 |
| `HNIEOJ_JUDGE_TEMP_TOKEN_NONCE_TTL_SECONDS` | `HNIEOJ_JUDGE_NODE_NONCE_TTL_SECONDS`（默认 300） | 认证挑战 / HTTP 签名 nonce 防重放窗口 |
| `HNIEOJ_JUDGE_TEMP_TOKEN_TTL_SECONDS` | `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_TTL_SECONDS`（默认 900） | 短期令牌 TTL，且被节点硬截止封顶 |
| `HNIEOJ_JUDGE_FORMAL_TOKEN_*`（Nacos 下发正式密钥） | 无 | 正式密钥初始化路径退休，节点改走注册 + 密钥轮换 |

`HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET` 必须由运行时环境/文件注入，不得写入普通 Nacos 配置；
`HNIEOJ_JUDGE_NODE_AUDIENCE` 必须所有副本一致。非法值（缺失密钥、非正 TTL、越界帧大小）启动即失败。

### 旧节点重新注册（re-enrollment）

1. 维护窗口内由管理员为每台旧节点生成一次性 Bootstrap：节点类型（formal/temp）、并发、权重、
   支持判题模式、Bootstrap 过期时间；temp 节点还必须给出大于当前的 `authorizationUntil`。
2. 节点本地用 CSPRNG 生成 Ed25519 私钥（0700 私有目录、0600 文件），先持久化私钥与
   `enrollmentId`，再申请挑战、提交注册证明。响应丢失时用同一 `enrollmentId` + 同一公钥重新申请
   挑战即可恢复，不会创建第二个节点；换用其他密钥会被拒绝。
3. 节点完成注册后以 WSS 认证，不再使用旧 bearer-only 令牌或 Nacos 下发的正式密钥。
4. 轮换：`prepare` 用当前 ACTIVE 密钥授权并由新密钥证明，`confirm` 由新密钥签名激活，旧密钥进入
   `rotationGraceSeconds` 宽限期；pending 超时自动过期，revoke 永远优先于 grace。

### 迁移与回滚边界

- 已有数据库在维护窗口内先备份，再按顺序执行增量迁移：
  `deploy/mysql/upgrade/20260919_redis_gateway.sql` → `deploy/mysql/upgrade/20260919_secure_node.sql`；
  顺序不可颠倒，fresh 安装使用完整初始化脚本且结果一致。
- 迁移后确认所有后端副本使用一致的 `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET` 与
  `HNIEOJ_JUDGE_NODE_AUDIENCE`，并建议 Redis 开启 AOF、`maxmemory-policy noeviction`。
- 回滚需先确认没有节点已切换到 v1 认证；v1 新表/新列保留，不做破坏性回滚。
- WSS 需经 TLS 终结（入口终止 TLS），维护窗口内允许已注册节点重连并重新认证。
- 以上为本地/预发验证边界，本仓库未在生产环境执行或验证。

### 会话纪元 / 访问版本语义

- **sessionEpoch**：每次成功的新连接认证在同事务内对 `judge_node_token` 行加锁并原子自增，
  旧连接即使物理未断也在业务操作上被拒绝；`AUTH_REFRESH` 不改变 epoch，且刷新挑战绑定
  创建时的物理连接、节点与 epoch，防止旧连接读取最新 epoch 伪装当前会话。
- **accessVersion**：正常密钥轮换**不**提升版本，旧密钥在 `rotationGraceSeconds` 内仍可完成
  在途任务；只有吊销 / 禁用 / 降权等紧急路径提升版本，令旧 NODE_ACCESS 令牌立即失效。
  HTTP 与 WSS 关键操作都在同一权威边界内校验令牌绑定的 `accessVersion` / `sessionEpoch` /
  公钥指纹与数据库值一致。
- **错误码**：WSS `ERROR.payload.code` 使用与 `ResultCode` 一致的数字码（400/401/403/409/500），
  `message` 描述具体原因，`retryable` 标明是否可重试；认证类失败不可在同一会话重试。
- **WSS 资源边界**：单实例并发连接上限 `max-connections`、单连接入站速率
  `message-rate-limit` / `message-rate-window-seconds`、空闲上限 `idle-timeout-seconds`、
  控制帧 `max-control-frame-bytes`、任务帧 `max-task-frame-bytes`、每连接有界写队列
  `writer-queue-capacity`；任一超限即拒绝或关闭连接（fail-closed）。
