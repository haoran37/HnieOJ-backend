CREATE TABLE IF NOT EXISTS `hnieoj_user_db`.`user_favorite` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` varchar(50) NOT NULL,
  `target_type` varchar(20) NOT NULL,
  `target_id` varchar(64) NOT NULL,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite` (`uid`, `target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
