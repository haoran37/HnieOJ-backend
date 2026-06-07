# Nacos Configs (Repo Snapshot)

该目录用于存放可公开、可版本管理的 Nacos 配置快照。

## 目录约定

```text
deploy/nacos/
  dev/
    DEFAULT_GROUP/                 # 可公开配置
    HNIEOJ_JUDGE_GROUP/            # 判题节点非敏感运行配置
      hnieoj-judge-node.yaml
    HNIEOJ_SECRET_GROUP/           # 仅存放示例模板
      hnieoj-secrets.example.yaml
      hnieoj-judge-formal-token.example.yaml
```

## 导入说明（dev namespace）

1. 登录 Nacos（namespace: `dev`）。
2. 先导入 `DEFAULT_GROUP` 下所有 `*.yaml`（Data ID 与文件名一致）。
3. 导入 `HNIEOJ_JUDGE_GROUP/hnieoj-judge-node.yaml`。该文件只保存判题节点缓存清理、心跳间隔、MQ 重试等非敏感运行参数。
4. 在 `HNIEOJ_SECRET_GROUP` 新建 `hnieoj-secrets.yaml`，内容参考 `hnieoj-secrets.example.yaml`。该文件只允许保留环境变量占位，不要填写真实密码或 Token。
5. 在 `HNIEOJ_SECRET_GROUP` 新建 `hnieoj-judge-formal-token.yaml`，内容可先参考 `hnieoj-judge-formal-token.example.yaml` 保持为空。
6. 真实敏感值只保存在服务器 `.env` 或进程环境变量中，不写入 Nacos。正式判题节点长期 Token 的密文由后端启动初始化或轮换接口自动发布到 `hnieoj-judge-formal-token.yaml`。

## 安全约束

- 禁止将真实 `hnieoj-secrets.yaml` 提交到仓库。
- 如需新增配置，优先补充示例模板与字段说明。
- `hnieoj-secrets.yaml` 只作为环境变量占位桥接，不保存真实 MySQL/Redis/RabbitMQ 密码、内部服务 Token 或 JWT Secret。
- `HNIEOJ_JUDGE_JWT_SECRET` 由 `hnieoj-judge` 容器环境变量直接注入，不再通过 Nacos Secret 分发。
- 后端只持有正式节点公钥和 Token 哈希，不持有正式节点私钥。
- 正式节点私钥只通过 `/etc/hnieoj/judge-security/judge_formal_private.pem` 文件挂载给 go-judge。
- `hnieoj-judge-formal-token.yaml` 只保存 `{rsa}` 密文、版本号和更新时间，不保存明文 Token。
- `hnieoj-judge-node.yaml` 不保存密码、私钥、节点私有路径和授权码；多判题机共享该配置，单节点差异用本地 `config.yaml` 或环境变量覆盖。
