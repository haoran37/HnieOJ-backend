CREATE TABLE IF NOT EXISTS `hnieoj_user_db`.`invite_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code_hash` char(64) NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` varchar(50) NOT NULL,
  `used_uid` varchar(50) DEFAULT NULL,
  `used_at` datetime DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code_hash` (`code_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次性注册邀请码，仅保存 SHA-256 摘要';
