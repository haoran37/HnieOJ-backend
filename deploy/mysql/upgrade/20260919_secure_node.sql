-- ============================================================
-- 节点身份协议 v1（Ed25519 + Bootstrap + 密钥轮换）增量迁移
-- 目标库：hnieoj_judge_db
-- 从当前 dev 的 judge_node_auth_code / judge_node_token 出发，
-- 扩展出节点注册事实、Bootstrap 绑定与公钥历史表，避免两套注册事实。
-- 不保存私钥 / bootstrap 明文 / accessToken 明文。
-- 幂等说明：本脚本为一次性增量，重复执行会因列已存在而报错，请勿重复应用。
-- ============================================================

-- 1. Bootstrap 授权码扩充：节点类型 / 策略 / 硬截止 / enrollment 绑定
ALTER TABLE `hnieoj_judge_db`.`judge_node_auth_code`
  ADD COLUMN `node_type` varchar(20) NOT NULL DEFAULT 'temp' COMMENT 'formal, temp',
  ADD COLUMN `policy_json` text DEFAULT NULL COMMENT '节点策略 JSON：maxConcurrency/supportedJudgeModes/weight/authorizationUntil',
  ADD COLUMN `authorization_until` datetime(3) DEFAULT NULL COMMENT '节点硬截止时间，formal 可空',
  ADD COLUMN `enrollment_id` varchar(64) DEFAULT NULL COMMENT 'Bootstrap 绑定的 enrollment ID，用于响应丢失恢复',
  ADD COLUMN `public_key_hash` varchar(64) DEFAULT NULL COMMENT '注册公钥 SHA-256 摘要',
  ADD COLUMN `node_id` varchar(64) DEFAULT NULL COMMENT '注册成功生成的节点 ID',
  ADD COLUMN `consumed_time` datetime DEFAULT NULL COMMENT 'Bootstrap 原子消费时间',
  ADD KEY `idx_enrollment_id` (`enrollment_id`);

-- 2. 节点注册事实扩充：会话纪元 / 访问版本 / 激活密钥 / 权重 / 排空 / 硬授权
ALTER TABLE `hnieoj_judge_db`.`judge_node_token`
  ADD COLUMN `session_epoch` bigint(20) NOT NULL DEFAULT '0' COMMENT '会话纪元，新会话接管时原子自增，旧连接业务写入被拒绝',
  ADD COLUMN `access_version` int(11) NOT NULL DEFAULT '0' COMMENT '访问版本，轮换/吊销时自增，用于短期令牌失效',
  ADD COLUMN `active_key_id` varchar(64) DEFAULT NULL COMMENT '当前激活密钥 ID（权威见 judge_node_key）',
  ADD COLUMN `weight` int(11) NOT NULL DEFAULT '10' COMMENT '调度权重',
  ADD COLUMN `draining` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否排空中',
  ADD COLUMN `authorization_until` datetime(3) DEFAULT NULL COMMENT '节点硬授权截止时间，formal 可空',
  ADD COLUMN `enrollment_id` varchar(64) DEFAULT NULL COMMENT '注册时使用的 enrollment ID',
  ADD UNIQUE KEY `uk_node_enrollment` (`enrollment_id`),
  ADD KEY `idx_active_key_id` (`active_key_id`),
  MODIFY COLUMN `expire_time` datetime(3) NOT NULL COMMENT '节点身份记录过期时间（硬截止，毫秒精度）';

-- 3. 公钥历史与轮换状态机（纯增量：当前 dev 无该表，不使用破坏性 DROP）
CREATE TABLE `hnieoj_judge_db`.`judge_node_key` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `key_id` varchar(64) NOT NULL COMMENT '密钥 ID',
  `node_id` varchar(64) NOT NULL COMMENT '所属节点 ID',
  `public_key` varchar(128) NOT NULL COMMENT 'Ed25519 裸公钥 Base64',
  `public_key_hash` varchar(64) NOT NULL COMMENT '公钥 SHA-256 摘要',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, ACTIVE, GRACE, REVOKED, EXPIRED',
  `rotation_id` varchar(64) DEFAULT NULL COMMENT '轮换 ID，初始密钥为空',
  `confirm_nonce` varchar(128) DEFAULT NULL COMMENT '轮换确认 nonce（随机，非私钥）',
  `expires_at` datetime(3) DEFAULT NULL COMMENT 'pending 过期或 grace 截止时间',
  `previous_key_id` varchar(64) DEFAULT NULL COMMENT '被本密钥轮换的旧密钥 ID',
  `activated_at` datetime DEFAULT NULL COMMENT '激活时间',
  `revoked_time` datetime DEFAULT NULL COMMENT '吊销时间',
  `revoked_by` varchar(50) DEFAULT NULL COMMENT '吊销管理员',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_id` (`key_id`),
  UNIQUE KEY `uk_node_rotation` (`node_id`, `rotation_id`),
  KEY `idx_node_status` (`node_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='判题节点公钥历史与轮换状态';
