-- ============================================================================
-- 升级脚本：B2 站内通知/消息、自助资料/密码、身份资料审核
-- 适用：已按 deploy/mysql/hnieoj_多数据库.sql 初始化，并升级过
--       20260918_redis_gateway.sql、20260920_remaining_b1.sql 的存量库。
-- 性质：纯增量（additive）。仅新增 3 张 hnieoj_user_db 表，不 DROP 任何历史表、
--       不 UPDATE/覆盖任何历史数据；脚本可重复执行。
-- 说明：
--   * 使用 CREATE TABLE IF NOT EXISTS，重跑安全；已存在的表不会被重建。
--   * 不使用任何破坏性 DDL，不需要重复数据预检。
--   * 全新安装请先执行 hnieoj_多数据库.sql（旧 schema），再执行本增量脚本。
--   * 本脚本不自动在生产执行；请在测试/预发库验证后由运维手动执行。
-- ============================================================================

USE `hnieoj_user_db`;

-- ============================================================================
-- 1. user_notice：管理员定向通知（草稿 -> 发布收件人快照）
--    target_type: USERS / CLASSES；target_spec: JSON 字符串数组（USERS 为 uid，
--    CLASSES 为班级 id）；status: DRAFT / PUBLISHED。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_notice` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL COMMENT '通知标题',
  `content` text NOT NULL COMMENT '通知正文（纯文本/安全 Markdown，后端不注入 HTML）',
  `target_type` varchar(20) NOT NULL COMMENT '目标类型：USERS/CLASSES',
  `target_spec` text NOT NULL COMMENT '目标ID的JSON字符串数组',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED',
  `creator_uid` varchar(50) NOT NULL COMMENT '创建者UID',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_gmt_create` (`status`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员定向通知';

-- ============================================================================
-- 2. user_message：发布通知时按收件人快照生成的站内消息
--    唯一键 (notice_id, recipient_uid) 保证重复发布不重复投递；
--    recipient_uid 查询索引服务本人收件箱/未读数；
--    deleted_at 为软删除，管理删除通知不级联删除已送达消息。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `notice_id` bigint(20) NOT NULL COMMENT '来源通知ID',
  `recipient_uid` varchar(50) NOT NULL COMMENT '收件人UID',
  `title` varchar(255) NOT NULL COMMENT '发布时标题快照',
  `content` text NOT NULL COMMENT '发布时正文快照',
  `read_at` datetime DEFAULT NULL COMMENT '已读时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_recipient` (`notice_id`, `recipient_uid`),
  KEY `idx_recipient_created` (`recipient_uid`, `created_at`),
  KEY `idx_recipient_read` (`recipient_uid`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息收件箱';

-- ============================================================================
-- 3. user_profile_change：用户身份（实名/学院/年级/班级）变更申请
--    original/proposed 为仅含 4 项身份字段的 JSON；
--    “同一用户仅一条待审”由业务层用户行锁保证，本表刻意不建唯一 pending 索引。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_profile_change` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` varchar(50) NOT NULL COMMENT '申请人UID',
  `original` text NOT NULL COMMENT '申请时原身份字段JSON',
  `proposed` text NOT NULL COMMENT '期望身份字段JSON',
  `reason` varchar(1000) NOT NULL COMMENT '申请原因',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED',
  `reviewer_uid` varchar(50) DEFAULT NULL COMMENT '审核人UID',
  `review_reason` varchar(1000) DEFAULT NULL COMMENT '审核意见/驳回原因',
  `review_at` datetime DEFAULT NULL COMMENT '审核时间',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_uid_status` (`uid`, `status`),
  KEY `idx_status_gmt_create` (`status`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户身份资料变更申请';
