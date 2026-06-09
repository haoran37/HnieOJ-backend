# HNieOJ 从零部署检查清单

这份清单用于服务器清空后重新部署，目标是确认后端、RabbitMQ 和判题节点能完整协同工作。

## 1. 前置服务

部署后端容器前，先准备：

- MySQL 8.0
- Redis
- Nacos 2.3.2
- Docker 与 Docker Compose 插件
- Git、Maven、OpenSSL

RabbitMQ 和 HNieOJ 二开 go-judge 判题节点可以通过 `deploy/scripts/deploy-dev.sh` 启动。

## 2. 数据库

清库重建时执行：

```bash
mysql -uroot -p < deploy/mysql/hnieoj_多数据库.sql
```

需要开发测试数据时再执行：

```bash
mysql -uroot -p < deploy/mysql/添加测试数据.sql
```

当前项目按“删库重建”方式维护初始化 SQL，暂不维护增量迁移脚本。

## 3. Nacos

导入这些配置：

- `deploy/nacos/dev/DEFAULT_GROUP/*.yaml`
- `deploy/nacos/dev/HNIEOJ_JUDGE_GROUP/hnieoj-judge-node.yaml`

按示例创建这些 secret group 配置：

- `HNIEOJ_SECRET_GROUP/hnieoj-secrets.yaml`
- `HNIEOJ_SECRET_GROUP/hnieoj-judge-formal-token.yaml`

规则：

- `hnieoj-secrets.yaml` 只保留环境变量占位符。
- 不要把真实 MySQL、Redis、RabbitMQ、内部 token 或判题 JWT secret 写入 Nacos。
- 正式判题节点 token 密文可以放在 Nacos。

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
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `RABBITMQ_VHOST`

脚本可以自动生成或补齐：

- `HNIEOJ_INTERNAL_TOKEN`
- `HNIEOJ_JUDGE_JWT_SECRET`
- `HNIEOJ_JUDGE_FORMAL_TOKEN_PUBLIC_KEY_PATH`
- `HNIEOJ_JUDGE_FORMAL_TOKEN_PRIVATE_KEY_PATH`
- `HNIEOJ_JUDGE_FORMAL_TOKEN_NACOS_DATA_ID`
- `HNIEOJ_JUDGE_FORMAL_TOKEN_NACOS_GROUP`

只想生成安全材料时，可以单独执行：

```bash
bash deploy/scripts/deploy-dev.sh security-init
```

## 5. 部署顺序

推荐顺序：

```bash
bash deploy/scripts/deploy-dev.sh rabbitmq-up
bash deploy/scripts/deploy-dev.sh deploy
bash deploy/scripts/deploy-dev.sh gojudge-up
```

查看容器状态：

```bash
bash deploy/scripts/deploy-dev.sh ps
bash deploy/scripts/deploy-dev.sh rabbitmq-ps
bash deploy/scripts/deploy-dev.sh gojudge-ps
```

查看日志：

```bash
bash deploy/scripts/deploy-dev.sh logs gateway
bash deploy/scripts/deploy-dev.sh logs hnieoj-submission
bash deploy/scripts/deploy-dev.sh logs hnieoj-judge
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

RabbitMQ 死信队列检查：

```bash
bash deploy/scripts/deploy-dev.sh judge-dlq-status
bash deploy/scripts/deploy-dev.sh judge-dlq-requeue 20
```

只有确认问题原因已经修复后，才执行死信重放。

## 7. 上线前安全检查

对外开放前确认：

- 只对公网暴露 gateway 端口。
- 服务端口、RabbitMQ AMQP、RabbitMQ 管理端、MySQL、Redis、Nacos 都应仅内网访问或受防火墙保护。
- `/etc/hnieoj/judge-security` 只允许可信部署用户读取。
- `.env` 权限保持为 `600`。
- 不要打印判题任务 payload，因为 MQ 消息中包含用户源码。
- RabbitMQ 使用独立 vhost 和最小权限账号。
- `HNIEOJ_JUDGE_JWT_SECRET` 与 `HNIEOJ_INTERNAL_TOKEN` 不是占位值。

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
bash deploy/scripts/deploy-dev.sh rabbitmq-down
```
