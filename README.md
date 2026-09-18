# HnieOJ-backend

> ⚠️ **项目状态：WIP**
> 当前仓库在合并服务的基础上，已将判题任务分发迁移为 Redis Streams + 内嵌认证 HTTP 网关，并接入二开 go-judge；多判题机调度、心跳隔离和集成测试仍在完善中。

[API文档](https://s.apifox.cn/91edc2c6-6918-4179-9852-9ec3742377c8)、[前端仓库](https://github.com/haoran37/HnieOJ)

## 项目状态

HnieOJ-backend 是一个基于 **Spring Cloud Alibaba** 的在线判题系统后端，采用微服务架构。  
当前阶段目标：在公开仓库前，把现状、已完成内容和后续计划透明化，便于后续维护者接手。

### 已完成

- 基础微服务骨架与网关转发
- 认证鉴权（Sa-Token）与内部服务调用约束（`/internal/**`）
- 用户、题目、提交、比赛、训练、讨论、公告、成就等模块的大部分接口
- submission -> Redis Streams 分发 + 认证任务网关 -> go-judge -> submission 的判题回调链路
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
├── hnieoj-user             # 用户服务（已合并原 hnieoj-auth 认证域与 hnieoj-achievement 成就域）
├── hnieoj-problem          # 题目服务
├── hnieoj-submission       # 提交服务（已合并原 hnieoj-judge 判题域）
├── hnieoj-contest          # 比赛服务
├── hnieoj-training         # 训练服务
├── hnieoj-discussion       # 讨论服务
└── hnieoj-announcement     # 公告/新闻服务
```

## 服务拓扑（8 个可执行服务 + 1 个公共依赖）

阶段一将原有的 11 个可执行服务合并为 8 个，对外 API、请求/响应结构、权限规则与消息格式均保持不变：

| 服务 | 默认端口 | 说明 |
| --- | --- | --- |
| gateway | 8800 | API 网关，统一路由与 Sa-Token 鉴权 |
| hnieoj-user | 8101 | 用户服务，合并原认证（auth）与成就（achievement）域，共用 user DB |
| hnieoj-problem | 8102 | 题目服务 |
| hnieoj-submission | 8103 | 提交服务，合并原判题（judge）域，共用 judge DB |
| hnieoj-contest | 8105 | 比赛服务 |
| hnieoj-training | 8106 | 训练/作业服务 |
| hnieoj-discussion | 8107 | 讨论区服务 |
| hnieoj-announcement | 8109 | 公告/新闻服务 |

- `common` 为公共依赖，不是可执行服务，也不计入 Docker Compose 的服务数量。
- 合并后认证（`/api/auth/**`、`/api/registrations/**`）与成就（`/api/users/*/achievements`、`/api/achievements/**`）由 `hnieoj-user` 提供，判题（`/api/system/**`、`/api/judge/**`、`/api/admin/judge/**`、`/judge/nodes/**`）由 `hnieoj-submission` 提供。
- 网关仍按原路径逐条路由，保留路由顺序与 `/ws/submissions/**` WebSocket 路由，不新增 `/api/admin/**` 单服务兜底路由。
- 合并进程内的原服务间调用改为本地 Service 调用；跨进程调用继续使用 Feign/WebClient，并指向合并后的服务 ID（`hnieoj-user` / `hnieoj-submission`）。

## 技术栈

- JDK 17
- Spring Boot 3.2.12
- Spring Cloud 2023.0.2
- Spring Cloud Alibaba 2023.0.1.2
- MySQL 8.0
- Nacos 2.3.2（注册中心 / 配置中心）
- Sa-Token（鉴权）
- Spring Cloud LoadBalancer
- Redis Streams（判题任务分发）
- MyBatis / MyBatis-Plus / Druid
- Redis
- Hutool
- Knife4j
- Maven

## 开发约定

- 版本固定：JDK 17、Spring Boot 3.2.12、Spring Cloud 2023.0.2、Spring Cloud Alibaba 2023.0.1.2，不升级主版本。
- 技术栈沿用 Sa-Token、MyBatis / MyBatis-Plus、Druid、Hutool、OpenFeign；不引入新的生产依赖。
- 判题任务分发统一使用 Redis Streams（XADD / XREADGROUP / ACK）；旧 RabbitMQ / AMQP 约定已废弃，不再新增相关依赖、配置或脚本。
- 代码风格：中文意图注释、英文日志 / 常量 / 语义命名；Controller 保持轻薄，业务集中在 Service；沿用现有 `Request` / `Vo` / `Dto` / `Mapper` / `Service` 命名与 `config` / `properties` / `exception` / `interceptor` / `feign` 目录布局；不新增顶层包或框架。
- 路由约定：管理端接口按业务域分散在各服务（如 `/api/admin/judge/**`），网关不提供 `/api/admin/**` 单服务兜底路由。
- 配置发布：仓库仅保存 `deploy/nacos/dev` 快照，远程 Nacos 由运维人工上传；仓库不再包含旧 `.idea` 配置目录与 Windows 判题机路径；判题机以同级 go-judge 当前源码为准。
- 兼容性：除已批准的判题节点运行时协议迁移外，对外业务 API 及其请求 / 响应 JSON 保持不变。

## 启动方式（开发环境）

### 前置依赖

请先自行准备并启动：

- MySQL 8.0
- Redis
- Nacos 2.3.2
- Redis 7.x（判题任务分发，需 AOF + noeviction）
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
mvn -pl hnieoj-user spring-boot:run
mvn -pl hnieoj-submission spring-boot:run
```

### 常用命令

```bash
# 全量测试（单元测试；依赖本机 MySQL8/Redis7.4 的真实集成测试默认跳过）
mvn test

# 显式运行真实集成测试（需要本机任务测试容器，见 deploy/MIGRATION-redis-gateway.md）
mvn -pl hnieoj-submission -am test -Dit.enabled=true

# 指定模块测试
mvn -pl hnieoj-user test

# 使用仓库内 Maven settings（如需要）
mvn -s deploy/maven/settings.xml clean install -DskipTests
```

## Docker Compose 开发部署

本仓库提供服务器 Shell 脚本 + Docker Compose 编排，默认管理后端服务；本地 Redis 和二开 go-judge 提供可选 Compose 组件，MySQL、Nacos 继续使用外部已部署实例。

- Compose 文件：`deploy/docker/docker-compose.dev.yml`
- 可选 Redis Compose 文件：`deploy/docker/docker-compose.redis.yml`
- 迁移与部署说明：`deploy/MIGRATION-redis-gateway.md`
- 可选 go-judge Compose 文件：`deploy/docker/docker-compose.gojudge.yml`
- 环境变量示例：`deploy/docker/.env.example`
- 一键部署脚本：`deploy/scripts/deploy-dev.sh`
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
bash deploy/scripts/deploy-dev.sh redis-up
bash deploy/scripts/deploy-dev.sh gojudge-up
```

## 配置管理（Nacos）

当前项目配置依赖 Nacos，建议区分为：

- `DEFAULT_GROUP`：可公开的业务配置（端口、开关、路由、非敏感参数）
- `HNIEOJ_SECRET_GROUP`：敏感配置（数据库密码、Redis 密码、内部 token、节点 JWT Secret 等）

判题节点不连接 Nacos，也没有节点专用 Nacos 分组：节点使用同级 `go-judge` 仓库的 `deploy/config.formal.example.yaml`（正式）或 `deploy/config.temp.example.yaml`（临时）生成本地 `config.yaml` 与逐节点凭证文件。

仓库中的 Nacos 配置快照目录：`deploy/nacos`（详见 `deploy/nacos/README.md`）。

> 注意：实际使用中敏感配置应当使用环境变量注入，当前使用 Nacos 配置仅便于开发环境

## 手动发布 Nacos 配置与受控上线

本仓库只保存 Nacos 配置快照（`deploy/nacos/dev`），仓库内容与脚本都不会连接或改写远程 Nacos，远程发布必须由运维人工完成。

手动发布步骤：

1. 在目标 namespace（如 `dev`）中，先导入 `deploy/nacos/dev/DEFAULT_GROUP/` 下的全部 `*.yaml`（Data ID 与文件名一致）。合并后只保留 8 个可执行服务对应的配置：`gateway`、`hnieoj-user`、`hnieoj-problem`、`hnieoj-submission`、`hnieoj-contest`、`hnieoj-training`、`hnieoj-discussion`、`hnieoj-announcement`，以及 `hnieoj-secrets.yaml`。
2. 原 `hnieoj-auth.yaml`、`hnieoj-achievement.yaml`、`hnieoj-judge.yaml` 不再发布；它们的字段已分别合并进 `hnieoj-user.yaml` 与 `hnieoj-submission.yaml`。发布完成后在目标 namespace 手动下线这三个旧 Data ID，避免残留旧配置。
3. 判题节点不使用 Nacos：正式节点参考同级 `go-judge` 仓库的 `deploy/config.formal.example.yaml`、临时节点参考 `deploy/config.temp.example.yaml`，各自生成本地 `config.yaml`，凭证由后端按节点签发。再按 `HNIEOJ_SECRET_GROUP` 下的示例模板人工创建 `hnieoj-secrets.yaml`（只保留环境变量占位，不写真实密码/Token）。原 `hnieoj-judge-formal-token.yaml`、`HNIEOJ_JUDGE_GROUP/hnieoj-judge-node.yaml` 不再使用，不要上传。
4. 人工逐项核对配置差异（数据库、Redis 地址与凭据保持环境变量占位；`hnieoj.judge.stream.*` 使用固定 Stream key）。判题链路的增量 SQL、节点/后端协同切换与 HTTPS 要求见 `deploy/MIGRATION-redis-gateway.md`。
5. 受控滚动发布，顺序建议：
   - 先发布 `hnieoj-user` 配置并重启该服务，确认 `/actuator/health`、`/api/auth/**`、`/api/users/*/achievements` 正常；
   - 再发布 `hnieoj-submission` 配置并重启该服务，确认 `/actuator/health`、判题回调、`/judge/nodes/**`、`/judge/tasks/**`、`/ws/submissions/**` 正常；
   - 最后发布 gateway 路由变更并重启 gateway，确认各业务路由与 WebSocket 路由转发正常；
   - 其余 problem/contest/training/discussion/announcement 服务按需滚动重启。
6. 如需回滚，按发布相反顺序执行：先回滚 gateway，再回滚 `hnieoj-submission` / `hnieoj-user` 及其配置。
7. 上线后确认服务发现中不再存在 `hnieoj-auth`、`hnieoj-achievement`、`hnieoj-judge` 实例，所有发现目标均指向合并后的服务。

> 以上为人工发布与受控上线流程说明；本仓库未在生产环境执行或验证该流程。

## 里程碑计划

- [x] 完成 submission -> Redis Streams 分发 + 认证任务网关 -> go-judge 的判题链路
- [x] 完成 go-judge 请求封装与状态映射
- [ ] 完成本地题目资源存储与 Nginx 静态图片代理的线上联调
- [ ] 支持多判题机负载均衡（节点管理、健康检查、故障摘除）
- [ ] 补齐判题链路集成测试与回归测试

## 贡献说明

欢迎提交 Issue / PR。  
但请注意：当前处于 WIP 且维护不稳定阶段，合并与反馈可能不及时。

## 许可证

本项目使用 [MIT License](./LICENSE) 开源。



