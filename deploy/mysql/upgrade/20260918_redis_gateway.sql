-- ============================================================================
-- 升级脚本：RabbitMQ -> Redis Streams + 节点 HTTP 认证网关
-- 适用：已存在 hnieoj_judge_db 的存量环境（全新安装请直接使用 hnieoj_多数据库.sql）
-- 性质：纯增量（additive），不删除、不重写任何业务数据。
-- 说明：
--   * judge_task_outbox 的 exchange_name / routing_key 为遗留 NOT NULL 列，代码写入固定标记
--     （exchange_name='redis-streams'，routing_key=Stream key）以满足约束，不参与路由；
--     真实路由使用新增的 stream_key / stream_id。
--   * judge_formal_token 表为遗留共享主密钥机制，迁移后不再参与鉴权，保留历史数据备查。
--   * 不负责执行生产迁移；请在本地/预发验证后由运维手动执行。
-- ============================================================================

USE `hnieoj_judge_db`;

-- 1. 节点短期 Token 增加授权期限 / 服务端核准并发 / draining 标记
ALTER TABLE `judge_node_token`
    ADD COLUMN `approved_max_concurrency` int(11) DEFAULT NULL COMMENT '服务端核准的最大并发额度，心跳上报不能提高' AFTER `revoked_by`,
    ADD COLUMN `authorization_until` datetime DEFAULT NULL COMMENT '临时节点首次接入授予的最晚授权期限，续期不得超过' AFTER `approved_max_concurrency`,
    ADD COLUMN `draining` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否排空：停止领取新任务但仍可完成在途任务' AFTER `authorization_until`;

-- 2. outbox 增加 Redis Streams 定位列（遗留 exchange_name / routing_key 保留但停用）
ALTER TABLE `judge_task_outbox`
    ADD COLUMN `stream_key` varchar(128) DEFAULT NULL COMMENT 'Redis Streams 固定 Stream key' AFTER `routing_key`,
    ADD COLUMN `stream_id` varchar(64) DEFAULT NULL COMMENT 'XADD 返回的消息 ID，用于终态 ACK' AFTER `stream_key`;

-- 3. 判题任务执行租约表：MySQL 权威所有权与执行资格
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
  `token_id` varchar(64) DEFAULT NULL COMMENT '当前租约持有 Token ID',
  `attempt_id` varchar(64) DEFAULT NULL COMMENT '当前执行尝试 UUID',
  `attempt_count` int(11) NOT NULL DEFAULT '0' COMMENT '实际领取执行次数；恢复/Redis 丢消息不增加',
  `max_attempt_count` int(11) NOT NULL DEFAULT '3' COMMENT '最大实际执行次数，超出置 SYSTEM_ERROR',
  `status` varchar(20) NOT NULL DEFAULT 'queued' COMMENT 'queued, leased, running, completed, failed',
  `lease_until` bigint(20) DEFAULT NULL COMMENT '租约到期时间，Unix 毫秒',
  `renew_after_millis` int(11) DEFAULT NULL COMMENT '建议续期间隔毫秒',
  `execution_deadline` bigint(20) DEFAULT NULL COMMENT '硬执行截止时间，Unix 毫秒；到期不可续租',
  `last_error` varchar(512) DEFAULT NULL COMMENT '最近一次失败/恢复原因',
  `terminal_fingerprint` varchar(64) DEFAULT NULL COMMENT '终态业务内容 SHA-256 指纹，用于同身份同 attempt 重报幂等校验；重判/重派时清空',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_id` (`submission_id`),
  KEY `idx_status_lease` (`status`, `lease_until`),
  KEY `idx_node_status` (`node_id`, `status`),
  KEY `idx_judge_task_id` (`judge_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='判题任务执行租约（权威所有权状态）';
