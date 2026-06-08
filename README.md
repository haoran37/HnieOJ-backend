# HnieOJ-backend

> ⚠️ **项目状态：WIP**
> 当前仓库已完成大部分业务接口，并已接入 RabbitMQ + 二开 go-judge 的基础判题链路；多判题机调度、心跳隔离和集成测试仍在完善中。

[API文档](https://s.apifox.cn/91edc2c6-6918-4179-9852-9ec3742377c8)、[前端仓库](https://github.com/haoran37/HnieOJ)

## 项目状态

HnieOJ-backend 是一个基于 **Spring Cloud Alibaba** 的在线判题系统后端，采用微服务架构。  
当前阶段目标：在公开仓库前，把现状、已完成内容和后续计划透明化，便于后续维护者接手。

### 已完成

- 基础微服务骨架与网关转发
- 认证鉴权（Sa-Token）与内部服务调用约束（`/internal/**`）
- 用户、题目、提交、比赛、训练、讨论、公告、成就等模块的大部分接口
- submission -> RabbitMQ -> go-judge -> submission 的基础判题回调链路
- Nacos 配置中心接入、MyBatis-Plus 持久层、统一返回结构（`Result/ResultCode`）

### 未完成

- 多判题机负载均衡与心跳/健康检查
- 判题链路集成测试与回归测试
- 本地题目资源存储的完整运维闭环（已改用 `/data/oj/problems`，仍需线上联调）
- 等等

## 模块概览

```text
HnieOJ-backend/
├── common                  # 公共模块（工具、常量、异常、通用配置）
├── gateway                 # API 网关
├── hnieoj-auth             # 认证服务
├── hnieoj-user             # 用户服务
├── hnieoj-problem          # 题目服务
├── hnieoj-submission       # 提交服务
├── hnieoj-judge            # 判题服务（WIP）
├── hnieoj-contest          # 比赛服务
├── hnieoj-training         # 训练服务
├── hnieoj-discussion       # 讨论服务
├── hnieoj-announcement     # 公告/新闻服务
└── hnieoj-achievement      # 成就服务
```

## 技术栈

- JDK 17
- Spring Boot 3.2.12
- Spring Cloud 2023.0.2
- Spring Cloud Alibaba 2023.0.1.2
- MySQL 8.0
- Nacos 2.3.2（注册中心 / 配置中心）
- Sa-Token（鉴权）
- Spring Cloud LoadBalancer
- RabbitMQ（判题异步链路）
- MyBatis / MyBatis-Plus / Druid
- Redis
- Hutool
- Knife4j
- Maven

## 启动方式（开发环境）

### 前置依赖

请先自行准备并启动：

- MySQL 8.0
- Redis
- Nacos 2.3.2
- RabbitMQ（判题链路开发时需要）
- 二开 go-judge（可通过部署脚本启动）
- 本地文件系统目录 `/data/oj/problems`（题面、图片、测试数据）

### 配置Nacos

详细步骤见 [Nacos Configs](deploy/nacos/README.md)

### 配置数据库

在 `deploy/mysql/hnieoj_多数据库.sql` 提供了 mysql 的初始化 sql 脚本，可直接构建表结构

在 `deploy/mysql/添加测试数据.sql` 提供了添加测试的 sql 脚本，可用于开发测试

### 编译

```bash
mvn clean install -DskipTests
```

### 启动单个服务

```bash
mvn -pl gateway spring-boot:run
mvn -pl hnieoj-auth spring-boot:run
mvn -pl hnieoj-user spring-boot:run
```

### 常用命令

```bash
# 全量测试
mvn test

# 指定模块测试
mvn -pl hnieoj-user test

# 使用仓库内 Maven settings（如需要）
mvn -s deploy/maven/settings.xml clean install -DskipTests
```

## Docker Compose 开发部署

本仓库提供服务器 Shell 脚本 + Docker Compose 编排，默认管理后端服务；RabbitMQ 和二开 go-judge 提供可选 Compose 组件，MySQL、Redis、Nacos 继续使用外部已部署实例。

- Compose 文件：`deploy/docker/docker-compose.dev.yml`
- 可选 RabbitMQ Compose 文件：`deploy/docker/docker-compose.rabbitmq.yml`
- 可选 go-judge Compose 文件：`deploy/docker/docker-compose.gojudge.yml`
- 环境变量示例：`deploy/docker/.env.example`
- 一键部署脚本：`deploy/scripts/deploy-dev.sh`
- 从零部署检查清单：`docs/deployment-readiness.md`
- 默认拉取分支：`dev`
- 默认部署目录：`/opt/hnieoj/backend`
- 默认 Gateway 端口映射：`8800:8800`

首次部署直接执行 `bash deploy/scripts/deploy-dev.sh`。如果 `/opt/hnieoj/backend/.env` 不存在，脚本会自动生成模板并中止；填入真实密钥后重新执行即可。

私有仓库使用 HTTPS 拉取时，需要在服务器执行前设置 `GIT_TOKEN`，该 Token 只用于 Git 拉取代码，不会写入仓库。

若 Docker Hub 拉取基础镜像较慢，可通过 `JAVA_BASE_IMAGE` 指定可访问的 Java 17 运行时镜像，或在服务器配置 Docker registry mirror。

服务器源码目录只作为部署目录使用，默认部署会丢弃其中的本地改动并对齐远端 `dev` 分支。

常用运维命令：

```bash
bash deploy/scripts/deploy-dev.sh ps
bash deploy/scripts/deploy-dev.sh logs gateway
bash deploy/scripts/deploy-dev.sh restart hnieoj-user
bash deploy/scripts/deploy-dev.sh rabbitmq-up
bash deploy/scripts/deploy-dev.sh gojudge-up
```

## 配置管理（Nacos）

当前项目配置依赖 Nacos，建议区分为：

- `DEFAULT_GROUP`：可公开的业务配置（端口、开关、路由、非敏感参数）
- `HNIEOJ_JUDGE_GROUP`：判题节点非敏感运行配置（缓存清理、心跳间隔、MQ 重试等）
- `HNIEOJ_SECRET_GROUP`：敏感配置（数据库密码、Redis 密码、内部 token、MQ 凭证等）

仓库中的 Nacos 配置快照目录：`deploy/nacos`（详见 `deploy/nacos/README.md`）。

> 注意：实际使用中敏感配置应当使用环境变量注入，当前使用 Nacos 配置仅便于开发环境

## 里程碑计划

- [x] 完成 submission -> RabbitMQ -> go-judge 的基础异步判题链路
- [x] 完成 go-judge 请求封装与状态映射
- [ ] 完成本地题目资源存储与 Nginx 静态图片代理的线上联调
- [ ] 支持多判题机负载均衡（节点管理、健康检查、故障摘除）
- [ ] 补齐判题链路集成测试与回归测试

## 贡献说明

欢迎提交 Issue / PR。  
但请注意：当前处于 WIP 且维护不稳定阶段，合并与反馈可能不及时。

## 许可证

本项目使用 [MIT License](./LICENSE) 开源。



