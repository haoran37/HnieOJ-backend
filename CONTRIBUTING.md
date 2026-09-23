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
mvn -pl hnieoj-gateway spring-boot:run
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

- `feat(hnieoj-gateway): add admin discussion route`
- `fix(auth): handle empty role cache`
- `docs: update judge deployment guide`

## 7. 分支策略与 Pull Request 要求

分支模型为 `master`（发布）+ `dev`（集成）：请一律基于 `dev` 分支提交 PR，经 CI 门禁与维护者审核后合入 `dev`；积累稳定后由维护者合并 `dev` → `master` 并打版本 tag 发布。

提交 PR 前请自检：

- [ ] 改动范围明确，且符合模块边界
- [ ] 本地 `mvn -B -ntp package` 编译与单元测试通过
- [ ] 不包含敏感信息或本地环境私有配置（CI 会运行 gitleaks 全历史扫描）
- [ ] 接口与配置说明沿用 README 与 `deploy/` 文档维护

CI 门禁（全部通过才可合并）：

- 构建 + 单元测试（ubuntu / windows 矩阵，JDK 17）
- MySQL8 + Redis7.4 服务容器集成回归（`-Dit.enabled=true`）
- 阿里巴巴 P3C 规约静态检查（存量违规见 `.ci/pmd-baseline-exclude.txt` 基线豁免，新增代码不豁免）
- gitleaks 密钥扫描；PR 另有依赖成分审查

---

再次感谢你的贡献！
