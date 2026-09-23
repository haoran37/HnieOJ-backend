# Docker Compose 开发环境部署

## 部署方式

当前只保留一种部署方式：在服务器上执行 `deploy/scripts/deploy-dev.sh`。

脚本会自动完成：

- 检查 `git`、`mvn`、`docker`、Docker Compose v2
- 检查 Docker daemon 权限
- 检查题目资源目录与 go-judge 缓存目录
- 使用 OpenSSL 自动生成判题 JWT Secret 与 NODE_ACCESS 运行时密钥（写入服务器 `.env`，不写入 Nacos）
- 拉取指定 Git 分支，默认 `dev`
- 使用 Maven 打包所有后端服务
- 使用 Docker Compose 构建并启动所有后端服务

## 目录约定

- 部署目录：`/opt/hnieoj/backend`
- 代码目录：`/opt/hnieoj/backend/source`
- 环境变量文件：`/opt/hnieoj/backend/.env`
- 题目资源目录：`/data/oj/problems`
- 判题节点状态目录：`/data/oj/judge-node`（容器内 `/var/lib/hnieoj-judge-node`，0700，保存 `identity.json`、`config.yaml`、`results/`）
- go-judge 配置文件（宿主机）：`/etc/hnieoj/go-judge/config.yaml`（只读挂载到容器内状态目录下的 `config.yaml`）
- 一次性 Bootstrap：目录 `/etc/hnieoj/judge-node`（0700）整目录只读挂载，目录内 `bootstrap.token`（0600）按需放置；挂载点与示例 `bootstrap.tokenFile` 一致
- go-judge 测试数据缓存目录：`/data/oj/judge-cache`

## 首次部署前准备

```bash
sudo mkdir -p /opt/hnieoj/backend/source /data/oj/problems
sudo mkdir -p /opt/hnieoj/go-judge/source /etc/hnieoj/go-judge /data/oj/judge-cache
sudo mkdir -p /data/oj/judge-node /etc/hnieoj/judge-node
sudo chown -R vipuser:vipuser /opt/hnieoj/backend /opt/hnieoj/go-judge /data/oj/problems /data/oj/judge-cache /data/oj/judge-node
sudo chmod 700 /data/oj/judge-node /etc/hnieoj/judge-node
# 只读 config.yaml bind 的宿主目标必须是常规文件，否则 Docker 会在状态目录里创建目录导致节点启动失败。
# 仅在缺失时创建 0600 空占位，绝不截断既有配置或触碰 identity.json/results。
if [[ ! -f /data/oj/judge-node/config.yaml ]]; then
  sudo touch /data/oj/judge-node/config.yaml && sudo chmod 600 /data/oj/judge-node/config.yaml
fi
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
- `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET`（节点 NODE_ACCESS 短期令牌密钥，运行时环境/文件注入，所有副本一致）

你只需要继续填写真实 MySQL、Redis、Nacos 等基础设施配置后，重新执行脚本即可。判题任务分发复用共享 Redis Streams，不再需要 RabbitMQ。节点身份使用 Bootstrap + Ed25519，私钥只保存在判题节点宿主机。

如只想补齐安全材料：

```bash
bash deploy/scripts/deploy-dev.sh security-init
```

## 可选 go-judge 容器

如果要在同一台服务器上运行二开后的 go-judge，可以使用脚本拉取 `go-judge` 仓库并启动两个容器：

- `go-judge-sandbox`：原始沙箱服务，仅在 `hnieoj-backend` 内部网络提供 `5050`，不对宿主机或公网发布端口
- `hnieoj-judge-node`：HnieOJ 判题节点，从 `HNIEOJ_STATE_DIR/config.yaml` 加载配置，通过 WSS `/ws/judge/node` 领取 Redis Streams 任务并回调后端

首次执行：

```bash
bash deploy/scripts/deploy-dev.sh gojudge-up
```

脚本会准备宿主机目录与权限：

- 状态目录 `${HNIEOJ_JUDGE_STATE_HOST_DIR:-/data/oj/judge-node}` 以 `0700` 创建并可写，容器内挂载到 `HNIEOJ_STATE_DIR`（默认 `/var/lib/hnieoj-judge-node`）。节点首次入网在该目录生成 `identity.json`（本地 Ed25519 私钥）并把结果队列持久化到 `results/`。
- 脚本会在状态目录内非破坏性地预置 `${GOJUDGE_STATE_DIR}/config.yaml`（缺失时创建 0600 空占位，绝不截断既有文件或触碰 `identity.json`/`results`）；它是只读配置 bind 的宿主目标，缺少时 Docker 会把它创建成目录导致节点启动失败。
- Bootstrap 目录 `${HNIEOJ_JUDGE_BOOTSTRAP_HOST_DIR:-/etc/hnieoj/judge-node}` 由脚本以 `0700` 创建并整目录只读挂载；一次性明文 `bootstrap.token`（0600）由管理员签发后放入目录内，挂载路径与示例 `bootstrap.tokenFile` 一致。已完成注册的节点重启无需新的 Bootstrap。
- 配置文件 `${GOJUDGE_CONFIG_HOST_FILE:-/etc/hnieoj/go-judge/config.yaml}` 由运维准备并只读挂载到 `/var/lib/hnieoj-judge-node/config.yaml`，即 Agent 的实际读取路径；状态目录本身仍可写。

> 直连 Compose（不经过部署脚本）时，必须先由运维预置上述宿主机路径，否则容器启动会失败：
>
> ```bash
> sudo mkdir -p /data/oj/judge-node /etc/hnieoj/judge-node /data/oj/judge-cache
> sudo chmod 700 /data/oj/judge-node /etc/hnieoj/judge-node
> # 仅在缺失时创建 0600 空占位，绝不截断既有 config.yaml 或 identity.json。
> [[ -f /data/oj/judge-node/config.yaml ]] || { sudo touch /data/oj/judge-node/config.yaml && sudo chmod 600 /data/oj/judge-node/config.yaml; }
> # 首次入网前再由管理员把一次性明文放到 /etc/hnieoj/judge-node/bootstrap.token 并 chmod 600；
> # 目录内没有该文件也能启动，已完成注册的节点复用 identity.json 即可。
> ```
>
> 直连 Compose 时通过 `--env-file` 或 shell 环境提供 `HNIEOJ_JUDGE_STATE_HOST_DIR`、`GOJUDGE_CONFIG_HOST_FILE`、
> `HNIEOJ_JUDGE_BOOTSTRAP_HOST_DIR`、`GOJUDGE_CACHE_DIR`；`deploy/scripts/deploy-dev.sh gojudge-up` 会按
> “显式环境变量 > `.env` > 默认值”的优先级自动解析并准备这些路径（不会 source `.env`）。

如果 `/etc/hnieoj/go-judge/config.yaml` 不存在，脚本会从 go-judge 仓库的 `deploy/config.formal.example.yaml` 生成模板并中止。该示例同时适用于正式（formal）与临时（temp）节点，官方 `deploy/config.temp.example.yaml` 使用完全相同机制，仅 `node.type: temp` 与 `authorizationUntil` 不同。填写以下关键项后重新执行：

```yaml
node:
  name: judge-node-01
  type: formal                                   # 临时节点填 temp
  maxConcurrency: 2
hnieoj:
  # 远程后端必须 HTTPS；任务通道由 baseUrl 推导为 wss://host/ws/judge/node，
  # 节点不跳过证书校验，也不提供 skip-cert 开关。
  baseUrl: "https://oj.example.com"
  wssUrl: ""                                     # 任务通道必须 WSS
  # 必须等于后端 hnieoj.submission.judge.node-security.audience
  # （环境变量 HNIEOJ_JUDGE_NODE_AUDIENCE，当前默认 hnieoj-judge-node）；
  # baseUrl / wssUrl / audience 必须与后端入口一起修改，否则 WSS 认证会失败。
  audience: "hnieoj-judge-node"
identity:
  file: "/var/lib/hnieoj-judge-node/identity.json"
  stateDir: "/var/lib/hnieoj-judge-node"
bootstrap:
  # 运维把管理员签发的一次性明文 bootstrap 放到该 0600 文件（所在目录整目录只读挂载）。
  tokenFile: "/etc/hnieoj/judge-node/bootstrap.token"
wss:
  resultQueueDir: "/var/lib/hnieoj-judge-node/results"
testdata:
  cacheRoot: "/data/oj/judge-cache"
gojudge:
  # 沙箱仅在同网络内可达，不要改为宿主机公网地址。
  endpoint: "http://go-judge-sandbox:5050"
```

节点不再读取 Nacos，也不再使用共享正式 Token 或 Nacos 下发的密钥。正式与临时节点统一通过 Bootstrap + Ed25519 注册后以 WSS 认证（`/ws/judge/node`），短期 `NODE_ACCESS` 令牌由后端签发并绑定 `nodeId/keyId/accessVersion/sessionEpoch`。管理员可为旧节点生成一次性 Bootstrap 重新入网。

后续需要主动轮换节点密钥时，节点走签名 HTTPS：

```http
POST /judge/nodes/keys/rotations
POST /judge/nodes/keys/rotations/{rotationId}/confirm
GET  /judge/nodes/keys/rotations/{rotationId}
```

旧共享正式 Token 轮换端点 `/api/admin/judge/nodes/formal-token/rotate` 已退休并显式拒绝。

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

注意：`gojudge-down` 不会删除 `/data/oj/judge-cache` 或状态目录，因此测试数据缓存与节点身份都会保留。缓存清理、心跳间隔等非敏感运行参数由节点本地 `config.yaml` 管理（多判题机各自维护），脚本中的 `gojudge-cache-clean` 仅用于临时手动清理缓存。

## 快捷命令

```bash
# 查看容器状态
bash deploy/scripts/deploy-dev.sh ps

# 查看全部服务日志
bash deploy/scripts/deploy-dev.sh logs

# 查看单个服务日志
bash deploy/scripts/deploy-dev.sh logs hnieoj-gateway
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
```

服务名与 `deploy/docker/docker-compose.dev.yml` 中的 8 个服务一致：`hnieoj-gateway`、`hnieoj-user`、`hnieoj-problem`、`hnieoj-submission`、`hnieoj-contest`、`hnieoj-training`、`hnieoj-discussion`、`hnieoj-announcement`。`common` 只是公共依赖，不单独启动。

go-judge 节点启用心跳后，会通过内部心跳上报节点运行状态和缓存状态。后台接口 `GET /api/admin/judge/nodes` 可查看 `online`、`runningTasks`、`maxConcurrency`、`cacheUsedBytes`、`cacheProblemCount`、`diskTotalBytes`、`diskFreeBytes` 等字段。缓存统计来自 `GOJUDGE_CACHE_DIR`，默认约 5 分钟采样一次，用于前端展示判题机负载、测试数据缓存占用和磁盘风险。

多判题机部署时，判题节点不再读取 Nacos；每台节点的非敏感运行参数写入本机 `config.yaml` 的 `testdata.maxCacheBytes`、`testdata.maxUnusedDuration`、`testdata.cleanupInterval`、`wss.heartbeatInterval` 等字段，节点名称、`identity.file`、`bootstrap.tokenFile` 也保存在该配置文件与宿主机只读挂载中。需要跨节点统一的值请由运维同步模板后分别下发。

## 检查命令

```bash
docker compose \
  -p hnieoj-dev \
  --env-file /opt/hnieoj/backend/.env \
  -f /opt/hnieoj/backend/source/deploy/docker/docker-compose.dev.yml \
  ps

curl http://127.0.0.1:8800/actuator/health
```
