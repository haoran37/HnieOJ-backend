# HnieOJ 后端项目

基于 Spring Cloud Alibaba 的微服务架构在线判题系统。

## 项目结构

```
HnieOJ-backend/
├── common/                    # 公共模块（工具、常量、异常等）
├── gateway/                   # API网关
├── hnieoj-announcement/       # 公告新闻服务
├── hnieoj-auth/               # 认证服务
├── hnieoj-user/               # 用户服务
├── hnieoj-problem/            # 题目服务
├── hnieoj-submission/         # 提交服务
├── hnieoj-judge/              # 判题服务
├── hnieoj-contest/            # 比赛服务
├── hnieoj-training/           # 训练服务
├── hnieoj-discussion/         # 讨论服务
├── hnieoj-achievement/        # 成就服务
├── docker-compose.yml         # 本地开发容器编排
└── Dockerfile                 # 通用Dockerfile
```

## 技术栈

- **JDK**: 17
- **Spring Boot**: 3.2.12
- **Spring Cloud**: 2023.0.2
- **Spring Cloud Alibaba**: 2023.0.1.2
- **Nacos**: 2.3.2（注册中心 & 配置中心）
- **Redis**: 7.0（缓存 & 登录态）
- **MySQL**: 8.0（数据存储）
- **RocketMQ**: 5.1.0（消息队列）
- **Sa-Token**: 1.37.0（认证授权）
- **MyBatis-Plus**: 3.5.5（ORM框架）
- **Knife4j**: 3.0.3（API文档）

## 本地开发环境搭建

### 前置要求

- Docker & Docker Compose
- Maven 3.6+
- JDK 17+

### 启动步骤

#### 1. 启动基础服务（Nacos、MySQL、Redis、RocketMQ）

```bash
docker-compose up -d
```

等待所有容器启动完成（约30秒）。

#### 2. 初始化数据库

```bash
# 数据库初始化脚本已通过 docker-compose 自动执行
# 如需手动执行，可运行：
mysql -h 127.0.0.1 -u root -p'YOUR_PASSWORD' < .idea/hnieoj_多数据库.sql
```

#### 3. 编译项目

```bash
mvn clean install -DskipTests
```

#### 4. 启动微服务（按顺序）

**方式一：IDE 启动（推荐开发时使用）**

在 IntelliJ IDEA 中分别运行以下启动类：

1. `gateway` → `GatewayApplication` (端口 8080)
2. `hnieoj-announcement` → `AnnouncementApplication` (端口 8109)
3. `hnieoj-user` → `UserApplication` (端口 8082)
4. `hnieoj-problem` → `ProblemApplication` (端口 8083)
5. `hnieoj-submission` → `SubmissionApplication` (端口 8084)
6. `hnieoj-judge` → `JudgeApplication` (端口 8085)
7. `hnieoj-contest` → `ContestApplication` (端口 8086)
8. `hnieoj-training` → `TrainingApplication` (端口 8087)
9. `hnieoj-discussion` → `DiscussionApplication` (端口 8088)
10. `hnieoj-achievement` → `AchievementApplication` (端口 8089)
11. `hnieoj-auth` → `AuthApplication` (端口 8090)

**方式二：命令行启动**

```bash
# 启动 gateway
mvn -pl gateway spring-boot:run

# 在新终端启动其他服务
mvn -pl hnieoj-user spring-boot:run
mvn -pl hnieoj-problem spring-boot:run
# ... 其他服务
```

### 本地开发配置

#### 最小启动集合（推荐）

如果资源有限，可只启动以下服务：

1. **gateway** - API网关（必需）
2. **hnieoj-user** - 用户服务（必需）
3. **hnieoj-problem** - 题目服务（必需）
4. **hnieoj-judge** - 判题服务（可选）

#### 访问地址

- **API 网关**: http://localhost:8080
- **Nacos 控制台**: http://localhost:8848/nacos
- **API 文档**: http://localhost:8080/doc.html

## 配置说明

### Nacos 配置

所有微服务的配置都可以在 Nacos 中心化管理。本地开发时，应用会优先读取本地 `application.yml` 配置。

### 数据库配置

- **用户数据库**: `hnieoj_user_db`
- **题目数据库**: `hnieoj_problem_db`
- **判题数据库**: `hnieoj_judge_db`
- **比赛数据库**: `hnieoj_contest_db`
- **训练数据库**: `hnieoj_training_db`
- **讨论数据库**: `hnieoj_discussion_db`
- **系统数据库**: `hnieoj_system_db`

## 常见问题

### 1. 启动时连接超时

检查 Nacos、MySQL、Redis 是否正常运行：

```bash
docker-compose ps
```

### 2. 数据库连接失败

确保 MySQL 容器已启动并初始化完成：

```bash
docker-compose logs mysql
```

### 3. 端口被占用

修改 `application.yml` 中的 `server.port` 配置。

## 代码规范

遵循 **阿里巴巴 Java 开发手册**：

- 命名语义清晰，禁止拼音命名
- 严禁魔法值，统一使用常量或枚举
- Controller 不允许写业务逻辑
- Service 层方法职责单一
- 禁止在 Controller 中直接操作数据库

## 命名规范

| 对象 | 命名规范 | 示例 |
|------|--------|------|
| 请求参数 | XxRequest | UserLoginRequest |
| 展示对象 | XxVo | UserVo |
| 数据传输 | XxDto | UserDto |
| 数据库实体 | 跟表名相同 | UserInfo |
| Service 接口 | XxService | UserService |
| Service 实现 | XxServiceImpl | UserServiceImpl |
| Mapper | XxMapper | UserMapper |
