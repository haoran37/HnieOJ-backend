# Docker Compose 开发环境部署

## 部署方式

当前只保留一种部署方式：在服务器上执行 `deploy/scripts/deploy-dev.sh`。

脚本会自动完成：

- 检查 `git`、`mvn`、`docker`、Docker Compose v2
- 检查 Docker daemon 权限
- 检查题目资源目录与判题安全目录
- 使用 OpenSSL 自动生成判题 JWT Secret、正式节点 RSA 公私钥
- 拉取指定 Git 分支，默认 `dev`
- 使用 Maven 打包所有后端服务
- 使用 Docker Compose 构建并启动所有后端服务

## 目录约定

- 部署目录：`/opt/hnieoj/backend`
- 代码目录：`/opt/hnieoj/backend/source`
- 环境变量文件：`/opt/hnieoj/backend/.env`
- 题目资源目录：`/data/oj/problems`
- 判题安全目录：`/etc/hnieoj/judge-security`
- go-judge 配置文件：`/etc/hnieoj/go-judge/config.yaml`
- go-judge 测试数据缓存目录：`/data/oj/judge-cache`

## 首次部署前准备

```bash
sudo mkdir -p /opt/hnieoj/backend/source /data/oj/problems /etc/hnieoj/judge-security
sudo mkdir -p /opt/hnieoj/go-judge/source /etc/hnieoj/go-judge /data/oj/judge-cache
sudo chown -R vipuser:vipuser /opt/hnieoj/backend /opt/hnieoj/go-judge /data/oj/problems /data/oj/judge-cache
```

部署用户需要能访问 Docker daemon，例如部署用户为 `vipuser`：

```bash
sudo usermod -aG docker vipuser
```

执行后需要重新登录服务器，或重启对应用户下的部署进程，确保用户组权限生效。验证命令：

```bash
sudo -u vipuser docker info
```

## 执行部署

在仓库目录执行：

```bash
bash deploy/scripts/deploy-dev.sh
```

该命令等同于：

```bash
bash deploy/scripts/deploy-dev.sh deploy
```

脚本开头提供了常用全局变量：

- `DEPLOY_BRANCH`：拉取分支，默认 `dev`
- `GATEWAY_PUBLIC_PORT`：网关宿主机端口，默认 `8800`
- `GATEWAY_SERVER_PORT`：网关容器内端口，默认 `8800`
- `JAVA_BASE_IMAGE`：Java 17 运行时基础镜像，默认 `eclipse-temurin:17-jre-jammy`
- `GIT_REPO_URL`：代码仓库地址，默认使用 HTTPS 地址
- `GIT_TOKEN`：私有仓库 HTTPS 拉取使用的 GitHub Token，不要写入仓库
- `DEPLOY_DIR`：部署根目录，默认 `/opt/hnieoj/backend`
- `DISCARD_LOCAL_CHANGES`：是否丢弃服务器源码目录本地改动，默认 `true`

服务器源码目录只作为部署目录使用，默认每次部署都会丢弃 `/opt/hnieoj/backend/source` 中的本地修改，并强制对齐远端 `dev` 分支。

临时覆盖示例：

```bash
DEPLOY_BRANCH=dev GATEWAY_PUBLIC_PORT=8800 bash deploy/scripts/deploy-dev.sh
```

如果服务器拉取 Docker Hub 很慢，可以使用可访问的镜像仓库覆盖基础镜像：

```bash
JAVA_BASE_IMAGE=你的镜像仓库/eclipse-temurin:17-jre-jammy bash deploy/scripts/deploy-dev.sh
```

也可以先在服务器配置 Docker registry mirror。注意：Ubuntu apt 源、Maven 源不会影响 `docker pull docker.io/...` 的速度，Docker 镜像源需要配置在 `/etc/docker/daemon.json`。

私有仓库需要提供 GitHub Token，推荐只给仓库读取权限：

```bash
export GIT_TOKEN=你的GitHubToken
bash deploy/scripts/deploy-dev.sh
```

脚本会通过临时 `GIT_ASKPASS` 传递 Token，不会把 Token 写入 `git remote -v`。

首次运行若 `/opt/hnieoj/backend/.env` 不存在，脚本会从 `deploy/docker/.env.example` 生成模板，并自动补齐：

- `HNIEOJ_INTERNAL_TOKEN`
- `HNIEOJ_JUDGE_JWT_SECRET`
- `HNIEOJ_JUDGE_FORMAL_TOKEN_PUBLIC_KEY_PATH`
- `HNIEOJ_JUDGE_FORMAL_TOKEN_PRIVATE_KEY_PATH`
- `HNIEOJ_JUDGE_FORMAL_TOKEN_NACOS_DATA_ID`
- `HNIEOJ_JUDGE_FORMAL_TOKEN_NACOS_GROUP`

脚本还会在 `/etc/hnieoj/judge-security` 下生成：

- `judge_formal_private.pem`：只挂载给正式 go-judge 节点
- `judge_formal_public.pem`：后端轮换正式 Token 时用于加密

你只需要继续填写真实 MySQL、Redis、RabbitMQ、Nacos 等基础设施配置后，重新执行脚本即可。

如只想补齐安全材料：

```bash
bash deploy/scripts/deploy-dev.sh security-init
```

## 可选 RabbitMQ 容器

如果服务器没有单独维护 RabbitMQ，可以使用项目提供的可选 Compose 文件启动 RabbitMQ：

```bash
bash deploy/scripts/deploy-dev.sh rabbitmq-up
```

该命令会使用同一个 `/opt/hnieoj/backend/.env`，默认创建：

- AMQP 端口：`5672`
- 管理后台端口：`15672`
- 用户：`hnieoj_judge`
- vhost：`hnieoj`
- 镜像：`rabbitmq:4.2.7-management`
- 数据目录：`/opt/hnieoj/rabbitmq/data`

对应 `.env` 推荐配置：

```env
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_PUBLIC_PORT=5672
RABBITMQ_MANAGEMENT_PUBLIC_PORT=15672
RABBITMQ_USERNAME=hnieoj_judge
RABBITMQ_PASSWORD=请填写强密码
RABBITMQ_VHOST=hnieoj
RABBITMQ_IMAGE=rabbitmq:4.2.7-management
RABBITMQ_DATA_DIR=/opt/hnieoj/rabbitmq/data
```

RabbitMQ 管理后台登录账号密码就是 `.env` 中的：

```env
RABBITMQ_USERNAME=...
RABBITMQ_PASSWORD=...
```

后端服务连接 RabbitMQ 也使用同一组账号、密码和 vhost。

如果你已经有外部 RabbitMQ，则不需要执行 `rabbitmq-up`，只需把 `.env` 改为外部地址，例如：

```env
RABBITMQ_HOST=host.docker.internal
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=hnieoj_judge
RABBITMQ_PASSWORD=请填写强密码
RABBITMQ_VHOST=hnieoj
```

查看 RabbitMQ：

```bash
bash deploy/scripts/deploy-dev.sh rabbitmq-ps
bash deploy/scripts/deploy-dev.sh rabbitmq-logs
```

查看判题任务队列与死信队列积压：
```bash
bash deploy/scripts/deploy-dev.sh judge-dlq-status
```

将判题死信队列中的消息重投回任务队列，默认最多重投 10 条，也可以显式指定数量：
```bash
bash deploy/scripts/deploy-dev.sh judge-dlq-requeue
bash deploy/scripts/deploy-dev.sh judge-dlq-requeue 20
```

该命令使用 RabbitMQ Management HTTP API，需要 `.env` 中的 `RABBITMQ_MANAGEMENT_URL`、`RABBITMQ_MANAGEMENT_USERNAME`、`RABBITMQ_MANAGEMENT_PASSWORD` 可用。使用项目自带 RabbitMQ 容器时默认指向 `http://127.0.0.1:15672`。

停止并移除 RabbitMQ 容器：

```bash
bash deploy/scripts/deploy-dev.sh rabbitmq-down
```

注意：`rabbitmq-down` 不会删除 `/opt/hnieoj/rabbitmq/data`，因此已有 vhost、用户和队列会保留。若数据目录已经初始化，修改 `.env` 中的 `RABBITMQ_DEFAULT_*` 相关值不会自动改写已有 RabbitMQ 用户，需要在管理后台或通过 `rabbitmqctl` 调整。

## 可选 go-judge 容器

如果要在同一台服务器上运行二开后的 go-judge，可以使用脚本拉取 `go-judge` 仓库并启动两个容器：

- `go-judge-sandbox`：原始沙箱服务，默认暴露 `5050`
- `hnieoj-judge-node`：HnieOJ 判题节点，消费 RabbitMQ 判题任务并回调后端

首次执行：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-up
```

如果 `/etc/hnieoj/go-judge/config.yaml` 不存在，脚本会从 go-judge 仓库的 `deploy/config.formal.example.yaml` 生成模板并中止。填写以下关键项后重新执行：

```yaml
hnieoj:
  baseUrl: "http://gateway:8800"
  formalToken:
    privateKeyPath: "/etc/hnieoj/judge-security/judge_formal_private.pem"
    nacos:
      serverAddr: "http://106.54.177.244:8848"
      namespace: "dev"
      group: "HNIEOJ_SECRET_GROUP"
      dataId: "hnieoj-judge-formal-token.yaml"
rabbitmq:
  host: "rabbitmq"
  password: "填写 RabbitMQ 密码"
gojudge:
  endpoint: "http://go-judge-sandbox:5050"
reporter:
  mode: "http"
```

正式节点长期 Token 不在配置文件中填写。`hnieoj-judge` 启动后如果数据库中没有 active 正式 Token，会自动生成 Token、保存哈希、使用公钥加密并发布到 Nacos。

后续需要主动轮换时，管理员调用：

```http
POST /api/admin/judge/nodes/formal-token/rotate
```

该接口会随机生成新的正式 Token、保存 SHA-256 摘要、使用公钥加密并发布到 Nacos。go-judge 节点会从 Nacos 读取密文并用本地私钥解密。

查看 go-judge：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-ps
bash deploy/scripts/deploy-dev.sh gojudge-logs
bash deploy/scripts/deploy-dev.sh gojudge-logs hnieoj-judge-node
```

停止并移除 go-judge 容器：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-down
```

注意：`gojudge-down` 不会删除 `/data/oj/judge-cache`，因此测试数据缓存会保留。

## 快捷命令

```bash
# 查看容器状态
bash deploy/scripts/deploy-dev.sh ps

# 查看全部服务日志
bash deploy/scripts/deploy-dev.sh logs

# 查看单个服务日志
bash deploy/scripts/deploy-dev.sh logs gateway
bash deploy/scripts/deploy-dev.sh logs hnieoj-user

# 重启全部服务
bash deploy/scripts/deploy-dev.sh restart

# 重启单个服务
bash deploy/scripts/deploy-dev.sh restart hnieoj-user

# 只拉取最新代码
bash deploy/scripts/deploy-dev.sh pull

# 只打包，不重启容器
bash deploy/scripts/deploy-dev.sh build

# 停止并移除 Compose 容器
bash deploy/scripts/deploy-dev.sh down

# 启动可选 RabbitMQ 容器
bash deploy/scripts/deploy-dev.sh rabbitmq-up
```

服务名与 `deploy/docker/docker-compose.dev.yml` 中的服务一致，例如 `gateway`、`hnieoj-auth`、`hnieoj-user`、`hnieoj-problem`、`hnieoj-submission`、`hnieoj-judge`。

## 检查命令

```bash
docker compose \
  -p hnieoj-dev \
  --env-file /opt/hnieoj/backend/.env \
  -f /opt/hnieoj/backend/source/deploy/docker/docker-compose.dev.yml \
  ps

curl http://127.0.0.1:8800/actuator/health
```
