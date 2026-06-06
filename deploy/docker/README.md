# Docker Compose 开发环境部署

## 部署方式

当前只保留一种部署方式：在服务器上执行 `deploy/scripts/deploy-dev.sh`。

脚本会自动完成：

- 检查 `git`、`mvn`、`docker`、Docker Compose v2
- 检查 Docker daemon 权限
- 检查题目资源目录与判题私钥目录
- 拉取指定 Git 分支，默认 `dev`
- 使用 Maven 打包所有后端服务
- 使用 Docker Compose 构建并启动所有后端服务

## 目录约定

- 部署目录：`/opt/hnieoj/backend`
- 代码目录：`/opt/hnieoj/backend/source`
- 环境变量文件：`/opt/hnieoj/backend/.env`
- 题目资源目录：`/data/oj/problems`
- 判题正式节点私钥目录：`/etc/hnieoj/judge-security`

## 首次部署前准备

```bash
sudo mkdir -p /opt/hnieoj/backend/source /data/oj/problems /etc/hnieoj/judge-security
sudo chown -R vipuser:vipuser /opt/hnieoj/backend /data/oj/problems
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

首次运行若 `/opt/hnieoj/backend/.env` 不存在，脚本会从 `deploy/docker/.env.example` 生成模板并中止。填入真实数据库、Redis、RabbitMQ、Nacos 与安全配置后，重新执行脚本即可。

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
