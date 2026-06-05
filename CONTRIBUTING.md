# Contributing Guide

感谢你为 HnieOJ-backend 做贡献！

本仓库当前处于 **WIP（开发中）** 状态，判题链路仍在建设中。欢迎提交 Issue / PR，但请优先保证改动可回滚、可验证、无敏感信息泄露。

## 1. 开发前须知

- 技术栈与版本请与仓库约定保持一致（JDK 17、Spring Boot 3.2.12、Spring Cloud 2023.0.2、Spring Cloud Alibaba 2023.0.1.2）。
- 遵循 Alibaba Java Coding Guidelines。
- 不主动引入新的重量级框架（如 OAuth2、Shiro、Seata）。

## 2. 分层与代码规范

- Controller 仅处理参数与返回，不写业务逻辑，不直接操作数据库。
- Service 方法职责单一，复杂组合逻辑下沉到 Manager/Repository。
- 命名规范：
  - 请求对象：`XxRequest`
  - 展示对象：`XxVo`
  - 传输对象：`XxDto`
  - Service：`XxService` / `XxServiceImpl`
  - DAO：`XxMapper`
- 严禁魔法值，统一使用常量或枚举。
- 注释使用中文，日志使用英文。

## 3. 接口与微服务约束

- 接口遵循 RESTful 风格。
- 统一返回 `Result` / `ResultCode`。
- 跨服务调用优先使用 OpenFeign。
- 仅内部接口使用 `/internal/**`。
- 后台接口按领域归属到对应服务，禁止集中转发到单一服务。

## 4. 本地开发命令

```bash
mvn clean install -DskipTests
mvn test
mvn -pl gateway spring-boot:run
```

如本地 Maven 源有特殊要求，可使用：

```bash
mvn -s deploy/maven/settings.xml clean install -DskipTests
```

## 5. 配置与安全

- 配置中心使用 Nacos。
- 请勿提交任何真实密钥（数据库密码、Redis 密码、内部 Token、MQ 凭证等）。
- 如需修改 Nacos 配置，请按仓库约定更新 `deploy/nacos` 下对应文件（公开配置 + 示例模板），真实上线由维护者手动同步。

## 6. 提交规范

Commit Message 使用 Conventional Commits，例如：

- `feat(gateway): add admin discussion route`
- `fix(auth): handle empty role cache`
- `docs: update judge deployment guide`

## 7. Pull Request 要求

提交 PR 前请自检：

- [ ] 改动范围明确，且符合模块边界
- [ ] 必要的单元测试/编译验证已通过
- [ ] 不包含敏感信息或本地环境私有配置
- [ ] 如改动接口，已同步更新 `.idea/API文档.md`
- [ ] 如改动较大，已补充 `.idea/codex.md` 工作记录

---

再次感谢你的贡献！

