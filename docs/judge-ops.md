# HNieOJ Judge Operations Runbook

## Scope

This document records the current operational checks for the HNieOJ judge pipeline:

```text
submission -> judge_task_outbox -> RabbitMQ -> hnieoj-judge-node -> result callback
```

## Backend Checks

### Judge Operation Summary

```http
GET /api/admin/submissions/judge-ops/summary
```

Permission: `problem:update`.

The response summarizes:

- outbox counts by status: `pending`, `processing`, `sent`, `failed`, `exhausted`
- retryable outbox count
- abnormal outbox count
- judging submission counts for `Pending`, `Compiling`, `Running`
- stale pending submission count
- stale active submission count
- long pending warning count
- warning codes for dashboard display

Suggested dashboard rule:

- `healthy = true`: no immediate action required.
- `outbox_exhausted`: inspect outbox detail and retry manually after fixing RabbitMQ or routing issues.
- `outbox_abnormal`: check RabbitMQ connectivity, exchange, queue, routing key, and publisher confirm logs.
- `stale_pending_submission`: check queue backlog and judge-node consumers.
- `stale_active_submission`: check judge-node execution and callback logs.
- `long_pending_submission`: warning only; do not mark as failed without checking queue backlog.

### Outbox Detail

```http
GET /api/admin/submissions/judge-outbox
POST /api/admin/submissions/judge-outbox/{id}/retry
```

Use these endpoints when the summary shows `failed` or `exhausted` outbox records.

## RabbitMQ Checks

When using the project deployment scripts, run:

```bash
bash deploy/scripts/deploy-dev.sh rabbitmq-ps
bash deploy/scripts/deploy-dev.sh rabbitmq-logs
```

Check:

- task queue has consumers
- task queue backlog is not continuously increasing
- DLQ does not keep growing
- RabbitMQ management UI is not exposed to the public internet

## DLQ Requeue

The deployment script provides DLQ requeue through RabbitMQ Management HTTP API:

```bash
bash deploy/scripts/deploy-dev.sh judge-dlq-requeue
bash deploy/scripts/deploy-dev.sh judge-dlq-requeue 20
```

Before requeue:

- verify the cause is fixed
- check judge-node logs
- check backend callback endpoint is reachable
- avoid repeatedly requeueing non-retryable judge failures

## Judge Node Checks

```http
GET /api/admin/judge/nodes
```

Check:

- `online`
- `lastHeartbeatTime`
- `runningTasks`
- `maxConcurrency`
- `supportedJudgeModes`
- `cacheUsedBytes`
- `cacheProblemCount`
- `diskFreeBytes`

If SPJ or interactive tasks are enabled, only publish them to nodes whose `supportedJudgeModes` include the required mode.

## Deployment Notes

- Keep RabbitMQ credentials in `.env`, not Nacos.
- Keep judge JWT secret in environment variables.
- Keep formal-node private key on the judge-node host only.
- Keep Nacos for non-sensitive runtime config and encrypted formal token ciphertext.
- Do not expose internal service ports, RabbitMQ AMQP, or RabbitMQ management to public networks.
