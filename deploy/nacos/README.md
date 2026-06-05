# Nacos Configs (Repo Snapshot)

该目录用于存放可公开、可版本管理的 Nacos 配置快照。

## 目录约定

```text
deploy/nacos/
  dev/
    DEFAULT_GROUP/                 # 可公开配置
    HNIEOJ_SECRET_GROUP/           # 仅存放示例模板
      hnieoj-secrets.example.yaml
```

## 导入说明（dev namespace）

1. 登录 Nacos（namespace: `dev`）。
2. 先导入 `DEFAULT_GROUP` 下所有 `*.yaml`（Data ID 与文件名一致）。
3. 在 `HNIEOJ_SECRET_GROUP` 新建 `hnieoj-secrets.yaml`，内容参考 `hnieoj-secrets.example.yaml`，填写真实值。
4. 真实密钥只保存在 Nacos 或环境变量，不提交到 Git。正式判题节点长期 Token 私钥建议通过环境变量或文件注入，Nacos 只保存公钥加密后的密文。

## 安全约束

- 禁止将真实 `hnieoj-secrets.yaml` 提交到仓库。
- 如需新增配置，优先补充示例模板与字段说明。
- `hnieoj.judge.formal-token.private-key` / `private-key-path` 不建议写入 Nacos 明文，生产环境优先使用环境变量占位或私钥文件挂载。
