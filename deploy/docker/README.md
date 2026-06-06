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

脚本开头提供了常用全局变量：

- `DEPLOY_BRANCH`：拉取分支，默认 `dev`
- `GATEWAY_PUBLIC_PORT`：网关宿主机端口，默认 `8800`
- `GATEWAY_SERVER_PORT`：网关容器内端口，默认 `8800`
- `GIT_REPO_URL`：代码仓库地址
- `DEPLOY_DIR`：部署根目录，默认 `/opt/hnieoj/backend`

临时覆盖示例：

```bash
DEPLOY_BRANCH=dev GATEWAY_PUBLIC_PORT=8800 bash deploy/scripts/deploy-dev.sh
```

首次运行若 `/opt/hnieoj/backend/.env` 不存在，脚本会从 `deploy/docker/.env.example` 生成模板并中止。填入真实数据库、Redis、RabbitMQ、Nacos 与安全配置后，重新执行脚本即可。

## 检查命令

```bash
docker compose \
  -p hnieoj-dev \
  --env-file /opt/hnieoj/backend/.env \
  -f /opt/hnieoj/backend/source/deploy/docker/docker-compose.dev.yml \
  ps

curl http://127.0.0.1:8800/actuator/health
```
