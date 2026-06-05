# Docker Compose 开发环境部署

## 目录约定

- 部署目录：`/opt/hnieoj/backend`
- 代码同步目录：`/opt/hnieoj/backend/source`
- 环境变量文件：`/opt/hnieoj/backend/.env`
- 题目资源目录：`/data/oj/problems`
- 判题正式节点私钥目录：`/etc/hnieoj/judge-security`

## 首次部署前准备

```bash
sudo mkdir -p /opt/hnieoj/backend/source /data/oj/problems /etc/hnieoj/judge-security
sudo cp deploy/docker/.env.example /opt/hnieoj/backend/.env
sudo chmod 600 /opt/hnieoj/backend/.env
sudo chown -R vipuser:vipuser /opt/hnieoj/backend /data/oj/problems
```

修改 `/opt/hnieoj/backend/.env` 中的数据库、Redis、RabbitMQ、内部 Token、判题 JWT Secret 和正式节点 Token 密文。

自托管 runner 用户需要能访问 Docker daemon。若 runner 用户为 `vipuser`，通常需要执行：

```bash
sudo usermod -aG docker vipuser
sudo systemctl restart actions.runner.*
```

## 手动部署命令

```bash
mvn -s deploy/maven/settings.xml clean package -DskipTests
docker compose \
  -p hnieoj-dev \
  --env-file /opt/hnieoj/backend/.env \
  -f deploy/docker/docker-compose.dev.yml \
  up -d --build --remove-orphans
```

Gateway 默认映射为 `8800:8800`，外部访问 `http://服务器IP:8800`。

## 检查命令

```bash
docker compose -p hnieoj-dev -f deploy/docker/docker-compose.dev.yml ps
curl http://127.0.0.1:8800/actuator/health
```
