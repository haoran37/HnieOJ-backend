# HNieOJ 判题链路运维手册

## 范围

本文记录 HNieOJ 当前判题链路的运维检查方式：

```text
submission -> judge_task_outbox -> RabbitMQ -> hnieoj-judge-node -> result callback
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
- `outbox_exhausted`：检查 outbox 详情，修复 RabbitMQ 或路由问题后手动重试。
- `outbox_abnormal`：检查 RabbitMQ 连通性、exchange、queue、routing key 和 publisher confirm 日志。
- `stale_pending_submission`：检查队列积压和 judge-node consumer。
- `stale_active_submission`：检查 judge-node 执行和回调日志。
- `long_pending_submission`：仅作告警，不要在未确认队列积压前直接标记失败。

### Outbox 详情

```http
GET /api/admin/submissions/judge-outbox
POST /api/admin/submissions/judge-outbox/{id}/retry
```

当摘要中出现 `failed` 或 `exhausted` outbox 记录时，使用这些接口查看详情并重试。

## RabbitMQ 检查

使用项目部署脚本时执行：

```bash
bash deploy/scripts/deploy-dev.sh rabbitmq-ps
bash deploy/scripts/deploy-dev.sh rabbitmq-logs
```

需要检查：

- 任务队列存在消费者
- 任务队列积压没有持续增长
- 死信队列没有持续增长
- RabbitMQ 管理后台没有暴露到公网

## 死信队列重放

部署脚本通过 RabbitMQ Management HTTP API 提供死信重放：

```bash
bash deploy/scripts/deploy-dev.sh judge-dlq-requeue
bash deploy/scripts/deploy-dev.sh judge-dlq-requeue 20
```

重放前必须确认：

- 问题原因已经修复
- judge-node 日志没有持续报错
- 后端回调接口可访问
- 不要反复重放不可重试的判题失败

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

## 临时节点绑定式 JWT

临时节点兑换 `/api/judge/temp-token` 时必须提交节点指纹和 Ed25519 公钥证明：

- `fingerprint.instanceId`：部署脚本持久化生成的节点实例 ID。
- `fingerprint.hostnameHash`、`fingerprint.machineIdHash`：节点环境摘要。
- `fingerprint.macAddressHashes`、`fingerprint.ipAddressHashes`：本机网络信息摘要。
- `fingerprint.supportedJudgeModes`：节点能力列表。
- `proof.type`：固定为 `ed25519`。
- `proof.publicKey`：Ed25519 公钥 Base64，可使用 X.509 公钥或 32 字节原始公钥。

后端会规范化指纹并返回 `fingerprintHash`，同时保存 `nodeId`、`tokenId`、`instanceId`、`boundSourceIp`、`publicKeyHash` 和过期时间。临时节点后续访问后端接口时必须追加以下请求头：

```http
X-Judge-Node-Id: <nodeId>
X-Judge-Token-Id: <tokenId>
X-Judge-Instance-Id: <instanceId>
X-Judge-Fingerprint: <fingerprintHash>
X-Judge-Signature-Algorithm: ed25519
X-Judge-Timestamp: <unixSeconds>
X-Judge-Nonce: <random>
X-Judge-Body-Sha256: <sha256Hex>
X-Judge-Signature: <base64Ed25519Signature>
```

签名串固定为：

```text
METHOD + "\n" +
PATH_WITH_QUERY + "\n" +
X-Judge-Body-Sha256 + "\n" +
X-Judge-Timestamp + "\n" +
X-Judge-Nonce
```

校验规则：

- JWT 必须有效、未过期，且 `tokenId` 未撤销。
- 请求来源 IP 必须与兑换时后端观测到的来源 IP 一致。
- `nodeId`、`tokenId`、`instanceId`、`fingerprintHash` 必须与 token 绑定记录一致。
- `X-Judge-Timestamp` 默认允许 300 秒偏差，可通过 `HNIEOJ_JUDGE_TEMP_TOKEN_ALLOWED_CLOCK_SKEW_SECONDS` 调整。
- `X-Judge-Nonce` 使用 Redis 做短期防重放，默认缓存 600 秒，可通过 `HNIEOJ_JUDGE_TEMP_TOKEN_NONCE_TTL_SECONDS` 调整。
- `X-Judge-Body-Sha256` 必须等于后端按原始请求体计算的 SHA-256。
- `X-Judge-Signature` 必须能通过绑定的 Ed25519 公钥验签。

节点摘要接口返回：

- 在线节点与离线 active 节点数量
- 正式节点与临时节点数量
- 总运行任务数与在线并发容量
- 按 judge mode 统计的在线节点数量
- 过载节点数量
- 低磁盘节点数量
- 即将过期的临时 token 数量
- dashboard 可直接展示的 warning code

## 部署注意事项

- RabbitMQ 凭证保存在 `.env`，不要写入 Nacos。
- 判题 JWT secret 使用环境变量注入。
- 正式节点私钥只保存在判题节点宿主机。
- Nacos 只保存非敏感运行配置和正式节点 token 密文。
- 不要向公网暴露内部服务端口、RabbitMQ AMQP 或 RabbitMQ 管理后台。
