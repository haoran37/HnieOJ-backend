# Docker Compose 开发环境部署

## 部署方式

当前只保留一种部署方式：在服务器上执行 `deploy/scripts/deploy-dev.sh`。

脚本会自动完成：

- 检查 `git`、`mvn`、`docker`、Docker Compose v2
- 检查 Docker daemon 权限
- 检查题目资源目录与判题节点凭证目录
- 使用 OpenSSL 自动生成判题 JWT Secret（不再生成共享正式节点 RSA 公私钥）
- 拉取指定 Git 分支，默认 `dev`
- 使用 Maven 打包所有后端服务
- 使用 Docker Compose 构建并启动所有后端服务

## 目录约定

- 部署目录：`/opt/hnieoj/backend`
- 代码目录：`/opt/hnieoj/backend/source`
- 环境变量文件：`/opt/hnieoj/backend/.env`
- 题目资源目录：`/data/oj/problems`
- 判题节点凭证目录：`/etc/hnieoj/judge-node`（宿主 0700，仅本节点运行凭证，续期后以 0600 原子替换）
- go-judge 配置文件：`/etc/hnieoj/go-judge/config.yaml`
- go-judge 测试数据缓存目录：`/data/oj/judge-cache`

## 首次部署前准备

```bash
sudo mkdir -p /opt/hnieoj/backend/source /data/oj/problems
sudo mkdir -p /opt/hnieoj/go-judge/source /etc/hnieoj/go-judge /data/oj/judge-cache
sudo mkdir -p /etc/hnieoj/judge-node && sudo chmod 700 /etc/hnieoj/judge-node
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

你只需要继续填写真实 MySQL、Redis、Nacos 等基础设施配置后，重新执行脚本即可。

如只想补齐安全材料：

```bash
bash deploy/scripts/deploy-dev.sh security-init
```

## 本地 Redis 容器（判题任务分发）

判题任务通过 Redis Streams 分发，Redis 必须开启 AOF 且使用 `noeviction`，并只允许后端内网访问。若服务器没有单独维护 Redis，可用项目提供的 Compose 文件在本机启动：

```bash
bash deploy/scripts/deploy-dev.sh redis-up
```

默认创建：

- 端口：`6379`（仅绑定 `127.0.0.1`）
- 持久化：`appendonly yes`，`appendfsync everysec`
- 内存策略：`noeviction`
- 镜像：`redis:7.4-alpine`
- 数据目录：`/opt/hnieoj/redis/data`

对应 `.env` 推荐配置：

```env
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_BIND_HOST=127.0.0.1
REDIS_PUBLIC_PORT=6379
REDIS_IMAGE=redis:7.4-alpine
REDIS_DATA_DIR=/opt/hnieoj/redis/data
```

查看与停止：

```bash
bash deploy/scripts/deploy-dev.sh redis-ps
bash deploy/scripts/deploy-dev.sh redis-logs
bash deploy/scripts/deploy-dev.sh redis-down
```

> 生产环境请把 Redis 放在私网/安全组内，绝不暴露给判题节点或公网；并配置监控与备份。


## 可选 go-judge 容器

如果要在同一台服务器上运行二开后的 go-judge，可以使用脚本拉取 `go-judge` 仓库并启动两个容器：

- `go-judge-sandbox`：原始沙箱服务，仅在 Compose 内网以 `go-judge-sandbox:5050` 可达，**默认不向宿主机发布端口**
- `hnieoj-judge-node`：HnieOJ 判题节点，通过后端 HTTPS 网关领取任务并回传进度/结果

沙箱是未认证的特权执行环境，只有判题节点需要访问，因此默认不暴露到宿主机。如确需本机调试，请使用同级 `go-judge` 仓库 `deploy/deploy-judge-node.sh` 中显式绑定 `GOJUDGE_BIND_ADDR=127.0.0.1` 的 `PUBLISH_GOJUDGE=true` 选项，不要让默认 Compose 放开公网端口。

首次执行：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-up
```

如果 `/etc/hnieoj/go-judge/config.yaml` 不存在，脚本会从 go-judge 仓库的 `deploy/config.formal.example.yaml` 生成模板并中止。填写以下关键项后重新执行（字段以同级 go-judge 当前源码为准）：

```yaml
hnieoj:
  # 远程后端必须是 HTTPS；节点只持运维交付的运行凭证文件（0600）
  baseUrl: "https://oj.example.com"
  credential:
    # 逐节点运行凭证文件；宿主目录 0700，并以可写目录方式挂载，续期原子替换后可被重启读到
    tokenFile: "/etc/hnieoj/judge-node/credential.json"
gojudge:
  endpoint: "http://go-judge-sandbox:5050"
reporter:
  mode: "http"
heartbeat:
  enabled: true
  endpoint: "/judge/nodes/heartbeat"
  interval: "30s"
```

正式节点不再有全局共享主密钥。管理员通过以下接口为每台正式节点签发独立凭证：

```http
POST /api/admin/judge/nodes/formal-tokens
```

返回体包含 `token` / `nodeId` / `tokenId` / `expireTime`；将返回的 data JSON 写入宿主 `/etc/hnieoj/judge-node/credential.json`（0600）后再启动节点，节点使用 `POST /judge/nodes/token/renew` 稳定续期（nodeId/tokenId 不变）。

临时节点首次接入使用管理员签发的授权码（由 `POST /api/admin/judge/nodes/auth-codes` 创建）：

```http
POST /api/judge/temp-token
```

首次兑换成功后节点自动把凭证以 0600 原子写入 `credential.tokenFile`，之后只走续期，不再兑换授权码。

排空与恢复接单：

- 停止领取新任务但完成在途任务：`POST /api/admin/judge/nodes/tokens/{tokenId}/draining`，body `{"draining": true}`。
- 节点自身心跳上报的 `draining=true` 只会被保留，不能清除管理员设置的 draining。
- 恢复接单：管理员调用同一接口并传 `{"draining": false}`；仅靠节点心跳无法清除管理员设置的 draining。
- 撤销凭证：`POST /api/admin/judge/nodes/tokens/{tokenId}/revoke`；撤销后该凭证无法再领取/续期/回传/下载测例。

查看 go-judge：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-ps
bash deploy/scripts/deploy-dev.sh gojudge-logs
bash deploy/scripts/deploy-dev.sh gojudge-logs hnieoj-judge-node
bash deploy/scripts/deploy-dev.sh gojudge-cache-status
bash deploy/scripts/deploy-dev.sh gojudge-cache-clean 7
```

停止并移除 go-judge 容器：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-down
```

注意：`gojudge-down` 不会删除 `/data/oj/judge-cache`，因此测试数据缓存会保留。缓存清理策略由节点本地 `config.yaml` 的 `testdata.*` 控制（参考 go-judge 仓库 `deploy/config.formal.example.yaml`）；脚本中的 `gojudge-cache-clean` 用于临时手动清理。

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

# 启动本地 Redis 容器（AOF + noeviction）
bash deploy/scripts/deploy-dev.sh redis-up
```

服务名与 `deploy/docker/docker-compose.dev.yml` 中的 8 个服务一致：`gateway`、`hnieoj-user`、`hnieoj-problem`、`hnieoj-submission`、`hnieoj-contest`、`hnieoj-training`、`hnieoj-discussion`、`hnieoj-announcement`。`common` 只是公共依赖，不单独启动。

go-judge 节点启用心跳后，会通过内部心跳上报节点运行状态和缓存状态。后台接口 `GET /api/admin/judge/nodes` 可查看 `online`、`runningTasks`、`maxConcurrency`、`cacheUsedBytes`、`cacheProblemCount`、`diskTotalBytes`、`diskFreeBytes` 等字段。缓存统计来自 `GOJUDGE_CACHE_DIR`，默认约 5 分钟采样一次，用于前端展示判题机负载、测试数据缓存占用和磁盘风险。

多判题机部署时，每台节点各自使用本地 `/etc/hnieoj/go-judge/config.yaml` 与逐节点凭证目录，不连接 Nacos。非敏感公共参数的默认值以同级 go-judge 仓库的 `deploy/config.formal.example.yaml` / `deploy/config.temp.example.yaml` 为参考；单节点差异用本地 `config.yaml` 或 go-judge 的 `HNIEOJ_*` 环境变量覆盖。

## 检查命令

```bash
docker compose \
  -p hnieoj-dev \
  --env-file /opt/hnieoj/backend/.env \
  -f /opt/hnieoj/backend/source/deploy/docker/docker-compose.dev.yml \
  ps

curl http://127.0.0.1:8800/actuator/health
```
