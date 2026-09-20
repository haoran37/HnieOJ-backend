# B1 增量迁移说明：标签 / 推荐 / 远程账号 / 新闻

对应脚本：`deploy/mysql/upgrade/20260920_remaining_b1.sql`

## 1. 适用范围与前置条件

- 适用于已按 `deploy/mysql/hnieoj_多数据库.sql` 初始化、并已执行 `20260918_redis_gateway.sql` 的存量环境。
- 全新安装顺序：先执行 `hnieoj_多数据库.sql`（旧 schema），再执行本增量脚本。
- 脚本只做增量变更，不含任何 `DROP TABLE` / `DROP COLUMN` / 删除数据操作，可重复执行。
- 不使用 MySQL 不支持的 `ADD COLUMN IF NOT EXISTS`，改用 `information_schema` 判断 + 预处理语句执行。
- 本脚本不自动在生产执行；请在测试/预发库验证后由运维手动执行。

## 2. 变更内容

| 库 | 对象 | 变更 | 说明 |
|---|---|---|---|
| `hnieoj_system_db` | `announcement.category` | 新增列 `varchar(20) NOT NULL DEFAULT 'ANNOUNCEMENT'` | 历史记录统一归入 `ANNOUNCEMENT`，不重分类、正文不变 |
| `hnieoj_judge_db` | `remote_judge_account` | 新增唯一索引 `uk_oj_username (oj, username)` | 并发创建/编辑时保证同一 OJ 下账号唯一 |
| `hnieoj_problem_db` | `problem_tag` | 新增普通索引 `idx_tid (tid)` | 标签被引用检测、推荐候选标签重合统计 |

## 3. 可重复执行

- `category` 列、`uk_oj_username`、`idx_tid` 均先查询 `information_schema`，已存在时执行 `SELECT 1` 空操作，不会重复创建。
- 重复执行不会修改任何业务数据。

## 4. 历史重复数据处理（唯一索引前）

`remote_judge_account` 加唯一索引前（且在本批任何 DDL 之前）会检测历史 `(oj, username)` 重复：

- MySQL 8 的 prepared statement 协议不支持 `SIGNAL`（`ERROR 1295`），因此脚本用本脚本专用临时存储过程
  `b1_precheck_remote_judge_account_duplicates` 承载条件 `SIGNAL SQLSTATE '45000'`：
  每次执行先 `DROP PROCEDURE IF EXISTS`，预检通过后立即 `DROP`，不在库中留下永久过程；
  预检失败时客户端中止、后续语句不执行，残留过程仅做只读校验，下次重跑会先清理。
- 若存在重复：脚本以 `SQLSTATE 45000` 明确中止，**不删除、不合并任何历史数据**，本批后续 DDL 不执行。
- 处理方式：管理员在测试/预生产库先人工核对重复账号（保留哪一条、如何合并），清理或改重后重新执行本脚本。
- 空重复时预检通过，脚本完整执行；重复执行同样通过（幂等）。

查询重复的 SQL（只读，可在执行迁移前预检）：

```sql
SELECT oj, username, COUNT(*) AS cnt, GROUP_CONCAT(id ORDER BY id) AS ids
FROM hnieoj_judge_db.remote_judge_account
GROUP BY oj, username
HAVING COUNT(*) > 1;
```

> 说明：重复检测的 `GROUP BY` 与唯一索引使用相同的 `utf8mb4_general_ci` 排序规则，因此检测结果与索引约束一致。

## 5. 验证

```sql
-- category 列存在且为 NOT NULL DEFAULT ANNOUNCEMENT
SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'hnieoj_system_db' AND TABLE_NAME = 'announcement' AND COLUMN_NAME = 'category';

-- 唯一索引存在
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'hnieoj_judge_db' AND TABLE_NAME = 'remote_judge_account' AND INDEX_NAME = 'uk_oj_username'
GROUP BY INDEX_NAME, NON_UNIQUE;

-- problem_tag.tid 索引存在
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'hnieoj_problem_db' AND TABLE_NAME = 'problem_tag' AND INDEX_NAME = 'idx_tid'
GROUP BY INDEX_NAME;
```

## 6. 回滚

回滚仅撤销本批新增对象，不涉及历史业务数据（但会丢失升级后写入的 `announcement.category` 值）：

```sql
-- 1) 远程账号唯一索引
ALTER TABLE `hnieoj_judge_db`.`remote_judge_account` DROP INDEX `uk_oj_username`;

-- 2) 题目标签 tid 索引
ALTER TABLE `hnieoj_problem_db`.`problem_tag` DROP INDEX `idx_tid`;

-- 3) 公告分类列（会丢失分类数据，请先备份）
ALTER TABLE `hnieoj_system_db`.`announcement` DROP COLUMN `category`;
```

回滚后如需再次升级，重新执行本增量脚本即可（脚本幂等）。

## 7. 与应用的关系

- 应用侧会同时按 `announcement.category` 过滤/写入；因此在应用发版前先完成本迁移，避免新代码访问不存在的列。
- `remote_judge_account` 的应用层重复校验依赖 `uk_oj_username` 兜底并发冲突；缺少索引时并发创建可能产生重复。
