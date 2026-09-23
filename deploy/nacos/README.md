# Nacos Configs (Repo Snapshot)

该目录用于存放可公开、可版本管理的 Nacos 配置快照。

## 目录约定

```text
deploy/nacos/
  dev/
    DEFAULT_GROUP/                 # 可公开配置（8 个后端服务 + hnieoj-secrets.yaml）
    HNIEOJ_JUDGE_GROUP/            # 已退休：判题节点 Agent 不再读取 Nacos
      hnieoj-judge-node.yaml
    HNIEOJ_SECRET_GROUP/           # 仅存放示例模板
      hnieoj-secrets.example.yaml
```

## 导入说明（dev namespace）

1. 登录 Nacos（namespace: `dev`）。
2. 先导入 `DEFAULT_GROUP` 下所有 `*.yaml`（Data ID 与文件名一致）。合并后只保留 8 个可执行服务：`hnieoj-gateway`、`hnieoj-user`、`hnieoj-problem`、`hnieoj-submission`、`hnieoj-contest`、`hnieoj-training`、`hnieoj-discussion`、`hnieoj-announcement`，以及 `hnieoj-secrets.yaml`。
3. 原 `hnieoj-auth.yaml`、`hnieoj-achievement.yaml` 的字段已合并进 `hnieoj-user.yaml`，`hnieoj-judge.yaml` 的字段已合并进 `hnieoj-submission.yaml`；发布后请手动下线这三个旧 Data ID。
4. 不要导入 `HNIEOJ_JUDGE_GROUP/hnieoj-judge-node.yaml`：判题节点 Agent 只读取本机 `HNIEOJ_STATE_DIR/config.yaml` 与环境变量，不再连接 Nacos；该文件仅为历史快照，保留在仓库中不参与运行，如目标 namespace 已有同名配置请手动下线。
5. 在 `HNIEOJ_SECRET_GROUP` 新建 `hnieoj-secrets.yaml`，内容参考 `hnieoj-secrets.example.yaml`。该文件只允许保留环境变量占位，不要填写真实密码或 Token。
6. 不再创建 `hnieoj-judge-formal-token.yaml`：共享正式 Token / RSA 密钥下发已退休，正式与临时节点统一通过 Bootstrap + Ed25519 注册。
7. 真实敏感值只保存在服务器 `.env` 或进程环境变量中，不写入 Nacos；`HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET` 缺失时后端启动 fail-fast。

## 手动发布与受控上线

- 本目录只是快照，仓库脚本不会连接远程 Nacos；所有远程发布由运维人工执行并复核。
- 受控上线建议顺序：先发 `hnieoj-user`，再发 `hnieoj-submission`，最后发 hnieoj-gateway 路由；回滚顺序相反。
- 上线后确认服务发现中不再有 `hnieoj-auth`、`hnieoj-achievement`、`hnieoj-judge` 实例。
- 本仓库未在生产环境执行或验证上述流程。

## 安全约束

- 禁止将真实 `hnieoj-secrets.yaml` 提交到仓库。
- 如需新增配置，优先补充示例模板与字段说明。
- `hnieoj-secrets.yaml` 只作为环境变量占位桥接，不保存真实 MySQL/Redis 密码、内部服务 Token、JWT Secret 或 `HNIEOJ_JUDGE_NODE_ACCESS_TOKEN_SECRET`。
- `HNIEOJ_JUDGE_JWT_SECRET` 由 `hnieoj-submission` 容器环境变量直接注入，不再通过 Nacos Secret 分发。
- 后端不持有节点 Ed25519 私钥；节点私钥只保存在判题节点宿主机（0700 目录 / 0600 文件）。
- 共享正式节点 Token 与 Nacos 下发密钥（RSA `hnieoj-judge-formal-token.yaml`）已退休；Nacos 不再存放任何节点凭据、公钥或密文。
- `hnieoj-judge-node.yaml` 已退休：判题节点 Agent 不再读取 Nacos，节点运行参数只来自本机 `config.yaml` 与环境变量，多判题机各自维护；Nacos 不保存密码、私钥、Bootstrap 明文或授权码。
