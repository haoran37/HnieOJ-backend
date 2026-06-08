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
