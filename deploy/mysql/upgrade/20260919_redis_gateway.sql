-- ============================================================
-- Redis Streams 判题数据平面增量迁移（outbox 定位列 + 执行租约表）
-- 目标库：hnieoj_judge_db
-- 从当前 dev 的 judge_task_outbox 出发，新增 Redis Streams 分发定位列与
-- MySQL 权威执行租约/尝试/额度/终态指纹表；纯增量，不删除任何已有表或数据。
-- 遗留 exchange_name / routing_key 为 NOT NULL 列，代码写固定标记以满足约束，
-- 真实分发使用新增的 stream_key / stream_id。
-- 幂等说明：本脚本为一次性增量，重复执行会因列/表已存在而报错，请勿重复应用。
-- ============================================================

USE `hnieoj_judge_db`;

-- 1. outbox 增加 Redis Streams 定位列
ALTER TABLE `judge_task_outbox`
  ADD COLUMN `stream_key` varchar(128) DEFAULT NULL COMMENT 'Redis Streams 固定 Stream key' AFTER `routing_key`,
  ADD COLUMN `stream_id` varchar(64) DEFAULT NULL COMMENT 'XADD 返回的消息 ID，用于终态原子 ACK+XDEL' AFTER `stream_key`;

-- 2. 判题任务执行租约表：MySQL 权威所有权、会话纪元与执行资格
CREATE TABLE IF NOT EXISTS `judge_task_execution` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` varchar(64) NOT NULL COMMENT '提交展示 ID',
  `judge_id` bigint(20) NOT NULL COMMENT 'judge 表主键',
  `judge_task_id` varchar(64) NOT NULL COMMENT '当前判题任务 ID，重判后更新',
  `problem_id` bigint(20) NOT NULL COMMENT '题目 DB ID',
  `problem_code` varchar(50) DEFAULT NULL COMMENT '题目展示 ID',
  `judge_mode` varchar(20) NOT NULL DEFAULT 'default' COMMENT 'default, spj, interactive',
  `stream_key` varchar(128) NOT NULL COMMENT 'Redis Streams key',
  `stream_id` varchar(64) DEFAULT NULL COMMENT '最近一次 XADD 的消息 ID',
  `node_id` varchar(64) DEFAULT NULL COMMENT '当前租约持有节点 ID',
  `token_id` varchar(64) DEFAULT NULL COMMENT '当前租约持有 registryID（等于 node_id，保留兼容列）',
  `session_epoch` bigint(20) DEFAULT NULL COMMENT '持有租约的物理会话纪元；重连 RESUME 迁移，旧会话写入被拒绝',
  `attempt_id` varchar(64) DEFAULT NULL COMMENT '当前执行尝试 UUID',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '实际领取执行次数；恢复/Redis 丢消息不增加',
  `max_attempt_count` int(11) NOT NULL DEFAULT '3' COMMENT '最大实际执行次数，超出置 SYSTEM_ERROR',
  `status` varchar(20) NOT NULL DEFAULT 'queued' COMMENT 'queued, leased, running, completed, failed',
  `lease_until` bigint(20) DEFAULT NULL COMMENT '租约到期时间，Unix 毫秒',
  `renew_after_millis` int(11) DEFAULT NULL COMMENT '建议续期间隔毫秒',
  `execution_deadline` bigint(20) DEFAULT NULL COMMENT '硬执行截止时间，Unix 毫秒；到期不可续租',
  `last_error` varchar(512) DEFAULT NULL COMMENT '最近一次失败/恢复原因',
  `terminal_fingerprint` varchar(64) DEFAULT NULL COMMENT '终态业务内容 SHA-256 指纹，用于同 attempt 重报幂等校验；重判/重派时清空',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_id` (`submission_id`),
  KEY `idx_status_lease` (`status`, `lease_until`),
  KEY `idx_node_status` (`node_id`, `status`),
  KEY `idx_judge_task_id` (`judge_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='判题任务执行租约（权威所有权状态）';
