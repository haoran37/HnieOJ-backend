# B2 增量迁移说明：站内通知 / 消息 / 身份资料审核

对应脚本：`deploy/mysql/upgrade/20260920_remaining_b2.sql`

## 1. 适用范围与前置条件

- 适用于已按 `deploy/mysql/hnieoj_多数据库.sql` 初始化，并已执行 `20260918_redis_gateway.sql`、
  `20260920_remaining_b1.sql` 的存量环境。
- 全新安装顺序：先执行 `hnieoj_多数据库.sql`（旧 schema），再依次执行历史增量脚本与本脚本。
- 脚本只新增 3 张 `hnieoj_user_db` 表，不含任何 `DROP TABLE` / `DROP COLUMN` / `UPDATE` / 删除数据操作，可重复执行。
- 使用 `CREATE TABLE IF NOT EXISTS`，重跑安全；已存在的表不会被重建或覆盖。
- 本脚本不自动在生产执行；请在测试/预发库验证后由运维手动执行。

## 2. 变更内容（均为新增表）

| 库 | 对象 | 关键字段/索引 | 说明 |
|---|---|---|---|
| `hnieoj_user_db` | `user_notice` | `target_type`、`target_spec`(JSON 数组)、`status`、`creator_uid`、`published_at`；`idx_status_gmt_create` | 管理员定向通知草稿与发布状态 |
| `hnieoj_user_db` | `user_message` | `notice_id`、`recipient_uid`、标题/正文快照、`read_at`、`deleted_at`、`created_at`；唯一键 `uk_notice_recipient (notice_id, recipient_uid)`；`idx_recipient_created`、`idx_recipient_read` | 发布时固定的收件人快照，重复发布不重复投递；本人收件箱/未读数查询索引 |
| `hnieoj_user_db` | `user_profile_change` | `uid`、`original`/`proposed`(JSON)、`reason`、`status`、`reviewer_uid`、`review_reason`、`review_at`；`idx_uid_status`、`idx_status_gmt_create` | 身份资料变更申请与原值/目标值 |

## 3. 可重复执行

- 3 张表全部使用 `CREATE TABLE IF NOT EXISTS`；重复执行是空操作。
- 脚本不读写任何历史业务表，不会覆盖或删除历史数据。
- 未创建 `(uid, status)` 上的“唯一待审”索引：同一用户仅一条待审申请由业务层
  `SELECT ... FOR UPDATE` 锁定 `user_info` 用户行保证（`AC5`/`AC6`），不依赖数据库唯一约束。

## 4. 验证

```sql
-- 3 张新表存在
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'hnieoj_user_db'
  AND TABLE_NAME IN ('user_notice', 'user_message', 'user_profile_change');

-- user_message 唯一键与查询索引
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'hnieoj_user_db' AND TABLE_NAME = 'user_message'
GROUP BY INDEX_NAME, NON_UNIQUE;

-- user_profile_change 索引（应无唯一 pending 索引）
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'hnieoj_user_db' AND TABLE_NAME = 'user_profile_change'
GROUP BY INDEX_NAME, NON_UNIQUE;
```

## 5. 回滚

本批对象是全新表，回滚仅涉及删除这 3 张新增表；**绝不能删除 `user_info`、`sys_class` 等历史业务表**：

```sql
-- 回滚前必须先确认这 3 张表内没有仍需保留的消息/申请数据，并按需备份。
-- 仅在测试/预发确认后执行；生产回滚需运维人工评估。
USE `hnieoj_user_db`;
DROP TABLE IF EXISTS `user_message`;
DROP TABLE IF EXISTS `user_notice`;
DROP TABLE IF EXISTS `user_profile_change`;
```

> 说明：`user_message` 通过 `notice_id` 逻辑引用 `user_notice`，删除通知管理记录不会级联删除已送达消息；
> 回滚脚本按要求同时删除三张表，属于“回滚本批能力”的取舍，不影响历史生产数据。
> 若只想撤销功能、保留数据，可只部署旧版应用而不执行任何 DDL。

## 6. 与应用的关系

- 应用侧启动后按新表读写；请在应用发版前完成本迁移，避免访问不存在的表。
- `user_message` 的唯一键是发布幂等的数据库兜底；应用层同时用通知行锁与状态判断保证重复发布不重复投递。
