/*
 * HnieOJ Database Schema (Multi-Database Architecture)
 * Version: 2.2.2
 * Date: 2026-02-12
 * Description: Database schema for HnieOJ refactored into microservices/multi-db structure.
 * Author: HaoRan Lyu
 */

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================================
-- 1. 用户与基础数据数据库: hnieoj_user_db
-- ========================================================
CREATE DATABASE IF NOT EXISTS `hnieoj_user_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `hnieoj_user_db`;

-- 学院表
DROP TABLE IF EXISTS `sys_college`;
CREATE TABLE `sys_college` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL COMMENT '学院名称',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学院基础信息表';

-- 班级表
DROP TABLE IF EXISTS `sys_class`;
CREATE TABLE `sys_class` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `college_id` bigint(20) NOT NULL COMMENT '所属学院ID',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级',
  `name` varchar(255) NOT NULL COMMENT '班级名称',
  `teacher_uid` varchar(50) DEFAULT NULL COMMENT '负责教师UID',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_college_grade` (`college_id`, `grade`),
  KEY `idx_teacher_uid` (`teacher_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级基础信息表';

-- 班级-助教关联表
DROP TABLE IF EXISTS `sys_class_ta`;
CREATE TABLE `sys_class_ta` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `class_id` bigint(20) NOT NULL COMMENT '班级ID',
  `ta_uid` varchar(50) NOT NULL COMMENT '助教UID',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_ta` (`class_id`, `ta_uid`) COMMENT '防止重复添加同一名助教',
  KEY `idx_ta_uid` (`ta_uid`) COMMENT '方便查询某位助教负责的所有班级'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级-助教关联表';

-- 用户表 (核心)
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info` (
  `uuid` varchar(32) NOT NULL COMMENT '系统内部UUID',
  `uid` varchar(50) NOT NULL COMMENT '学号/工号/用户id',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '加密密码',
  `email` varchar(255) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',
  `college_id` bigint(20) DEFAULT NULL COMMENT '关联学院ID',
  `class_id` bigint(20) DEFAULT NULL COMMENT '关联班级ID',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级',
  `realname` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `qq` varchar(20) DEFAULT NULL COMMENT 'QQ号',
  `status` int(11) DEFAULT '0' COMMENT '0:正常, 1:禁用',
  `cf_username` varchar(100) DEFAULT NULL COMMENT 'Codeforces账号',
  `github` varchar(255) DEFAULT NULL,
  `blog` varchar(255) DEFAULT NULL,
  `ip_restricted` tinyint(1) DEFAULT '0' COMMENT '是否开启IP限制',
  `ip_whitelist` json DEFAULT NULL COMMENT 'IP白名单 JSON Array',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`uuid`),
  UNIQUE KEY `uk_uid` (`uid`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 用户注册申请表 (审核用)
DROP TABLE IF EXISTS `user_register_apply`;
CREATE TABLE `user_register_apply` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` varchar(50) NOT NULL COMMENT '学号/工号/用户id',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `email` varchar(255) NOT NULL COMMENT '邮箱',
  `college_id` bigint(20) DEFAULT NULL COMMENT '申请学院ID',
  `class_id` bigint(20) DEFAULT NULL COMMENT '申请班级ID',
  `grade` varchar(20) DEFAULT NULL COMMENT '申请年级',
  `qq` varchar(20) DEFAULT NULL COMMENT 'QQ号',
  `status` int(11) DEFAULT '0' COMMENT '0:待审核, 1:通过, 2:驳回',
  `reply_info` varchar(255) DEFAULT NULL COMMENT '驳回原因',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户注册审核表';

-- 角色表
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `role` varchar(50) NOT NULL COMMENT '角色标识 (root, admin, teacher, ta, student)',
  `description` varchar(255) DEFAULT NULL,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

INSERT INTO `role` (`id`, `role`, `description`) VALUES 
(1000, 'root', '超级管理员'),
(1001, 'admin', '管理员'),
(1002, 'teacher', '教师'),
(1003, 'ta', '助教'),
(1004, 'student', '学生');

-- 权限表
DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL COMMENT '权限码',
  `name` varchar(100) NOT NULL COMMENT '权限名称',
  `description` varchar(255) DEFAULT NULL COMMENT '权限描述',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

INSERT INTO `permission` (`code`, `name`, `description`) VALUES
('user:manage', '用户管理', '管理用户信息'),
('user:ban', '封禁用户', '禁用用户账号'),
('problem:create', '创建题目', '新增题目'),
('problem:update', '修改题目', '修改题目内容'),
('problem:delete', '删除题目', '删除题目'),
('judge:rejudge', '重判', '重新判题'),
('contest:create', '创建比赛', '新增比赛'),
('contest:publish', '发布比赛', '发布比赛'),
('system:notice:publish', '发布公告', '发布系统公告');

-- 角色-权限关联表
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `permission_id` bigint(20) NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1000, id FROM permission;

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1001, id FROM permission
WHERE code <> 'user:ban';

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1002, id FROM permission
WHERE code IN ('problem:create','problem:update','judge:rejudge');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1003, id FROM permission
WHERE code IN ('judge:rejudge');

-- 用户-角色关联表
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_uid` varchar(50) NOT NULL COMMENT '用户UID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_uid`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 用户成就表
DROP TABLE IF EXISTS `user_achievement`;
CREATE TABLE `user_achievement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` varchar(50) NOT NULL COMMENT '用户UID',
  `title` varchar(255) NOT NULL COMMENT '成就标题',
  `content` text COMMENT '详情/描述',
  `proof_url` varchar(500) DEFAULT NULL COMMENT '证明材料URL',
  `achieve_time` datetime DEFAULT NULL COMMENT '获得时间',
  `status` int(11) DEFAULT '1' COMMENT '0:待审核, 1:已通过',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成就记录';

-- 成就申请审核表
DROP TABLE IF EXISTS `achievement_apply`;
CREATE TABLE `achievement_apply` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` varchar(50) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `file_url` varchar(500) NOT NULL,
  `status` varchar(20) DEFAULT 'pending' COMMENT 'pending, approved, rejected',
  `reason` varchar(255) DEFAULT NULL COMMENT '驳回原因',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成就认证申请表';


-- ========================================================
-- 2. 题目数据库: hnieoj_problem_db
-- ========================================================
CREATE DATABASE IF NOT EXISTS `hnieoj_problem_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `hnieoj_problem_db`;

-- 题目表
DROP TABLE IF EXISTS `problem`;
CREATE TABLE `problem` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `problem_code` varchar(50) NOT NULL COMMENT '题目展示ID',
  `title` varchar(255) NOT NULL,
  `author` varchar(100) DEFAULT 'admin',
  `type` int(11) DEFAULT '0' COMMENT '题目类型 (0: ACM模式, 1: OI模式)',
  `judge_mode` varchar(20) DEFAULT 'default' COMMENT '判题模式 (default: 文本比对, spj: 特判程序, interactive: 交互题)',
  `time_limit` int(11) DEFAULT '1000' COMMENT '时间限制 (ms)',
  `memory_limit` int(11) DEFAULT '256' COMMENT '内存限制 (mb)',
  `stack_limit` int(11) DEFAULT '128' COMMENT '栈限制 (mb)',
  `description` longtext COMMENT '题目描述',
  `input` longtext COMMENT '输入说明',
  `output` longtext COMMENT '输出说明',
  `examples` longtext COMMENT '题目样例 (JSON)',
  `hint` longtext COMMENT '题目提示信息',
  `difficulty` int(11) DEFAULT '0' COMMENT '难度等级 (0: 简单, 1: 中等, 2: 困难)',
  `auth` int(11) DEFAULT '1' COMMENT '访问权限 (1: 公开题目, 2: 私有题目, 3: 比赛专用)',
  `io_score` int(11) DEFAULT '100' COMMENT 'OI模式下的总分',
  `is_remote` tinyint(1) DEFAULT '0' COMMENT '是否为远程评测题目 (0: 本地, 1: 远程/VJudge)',
  `source` varchar(255) DEFAULT NULL COMMENT '题目来源',
  `spj_code` longtext COMMENT '特判程序 (SPJ) 的源代码内容',
  `spj_language` varchar(20) DEFAULT NULL COMMENT '特判程序的编译语言',
  `is_remove_end_blank` tinyint(1) DEFAULT '1' COMMENT '判题时是否自动去除行末空格 (1: 是, 0: 否)',
  `open_case_result` tinyint(1) DEFAULT '1' COMMENT '是否允许普通用户查看测试点详情 (1: 允许, 0: 隐藏)',
  `score_percentage` decimal(5, 2) DEFAULT '0.00' COMMENT '题目评分',
  `submission_count` int(11) DEFAULT '0' COMMENT '总提交次数',
  `accepted_count` int(11) DEFAULT '0' COMMENT '总通过次数',
  `data_version` int(11) DEFAULT '1' COMMENT '测试数据版本，每次测试数据变更后自增',
  `modified_user` varchar(100) DEFAULT NULL COMMENT '最后一次修改题目的管理员',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '题目创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '题目最近一次修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problem_code` (`problem_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目主表';

-- 题目标签
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `color` varchar(20) DEFAULT NULL,
  `category` varchar(50) DEFAULT NULL COMMENT '标签分类',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目标签';

-- 题目-标签关联
DROP TABLE IF EXISTS `problem_tag`;
CREATE TABLE `problem_tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `problem_id` bigint(20) NOT NULL,
  `tid` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_problem_id` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 语言配置
DROP TABLE IF EXISTS `language`;
CREATE TABLE `language` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '显示名称',
  `content_type` varchar(50) NOT NULL COMMENT '提交类型',
  `compile_command` varchar(255) DEFAULT NULL,
  `is_spj` tinyint(1) DEFAULT '0',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ========================================================
-- 3. 判题数据库: hnieoj_judge_db
-- ========================================================
CREATE DATABASE IF NOT EXISTS `hnieoj_judge_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `hnieoj_judge_db`;

-- 判题服务器 (后端判题机)
DROP TABLE IF EXISTS `judge_server`;
CREATE TABLE `judge_server` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) DEFAULT NULL,
  `ip` varchar(50) NOT NULL,
  `port` int(11) NOT NULL,
  `url` varchar(100) DEFAULT NULL,
  `cpu_core` int(11) DEFAULT '0',
  `task_number` int(11) DEFAULT '0',
  `max_task_number` int(11) DEFAULT '0',
  `status` int(11) DEFAULT '0' COMMENT '0:正常, 1:停用',
  `is_remote` tinyint(1) DEFAULT '0',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 临时判题节点授权码
DROP TABLE IF EXISTS `judge_node_auth_code`;
CREATE TABLE `judge_node_auth_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code_hash` varchar(128) NOT NULL COMMENT '授权码 SHA-256 摘要',
  `node_name` varchar(100) DEFAULT NULL COMMENT '预期节点名称',
  `created_by` varchar(50) DEFAULT NULL COMMENT '创建管理员',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `max_exchange_count` int(11) DEFAULT '1' COMMENT '最大兑换次数',
  `used_count` int(11) DEFAULT '0' COMMENT '已兑换次数',
  `status` varchar(20) DEFAULT 'enabled' COMMENT 'enabled, revoked, expired',
  `expire_time` datetime NOT NULL COMMENT '授权码过期时间',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_hash` (`code_hash`),
  KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临时判题节点授权码';

-- 判题节点短期 Token 审计记录
DROP TABLE IF EXISTS `judge_node_token`;
CREATE TABLE `judge_node_token` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `token_id` varchar(64) NOT NULL COMMENT 'JWT jti/tokenId',
  `node_id` varchar(64) NOT NULL COMMENT '判题节点 ID',
  `node_name` varchar(100) DEFAULT NULL COMMENT '节点名称',
  `node_type` varchar(20) NOT NULL COMMENT 'formal, temp',
  `status` varchar(20) DEFAULT 'active' COMMENT 'active, revoked, expired',
  `auth_code_id` bigint(20) DEFAULT NULL COMMENT '来源授权码 ID',
  `expire_time` datetime NOT NULL COMMENT 'Token 过期时间',
  `last_used_time` datetime DEFAULT NULL COMMENT '最近使用时间',
  `revoked_time` datetime DEFAULT NULL COMMENT '吊销时间',
  `revoked_by` varchar(50) DEFAULT NULL COMMENT '吊销管理员',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_id` (`token_id`),
  KEY `idx_node_id` (`node_id`),
  KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='判题节点短期 Token 审计记录';

-- 提交记录 (Status)
DROP TABLE IF EXISTS `judge`;
CREATE TABLE `judge` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submit_id` varchar(64) NOT NULL COMMENT 'UUID 用于展示',
  `problem_id` bigint(20) NOT NULL COMMENT '题目DB ID',
  `problem_code` varchar(50) NOT NULL COMMENT '题目展示ID',
  `uid` varchar(50) NOT NULL COMMENT '用户UID',
  `username` varchar(100) NOT NULL,
  `language` varchar(20) NOT NULL,
  `code` longtext NOT NULL,
  `status` int(11) DEFAULT '-10' COMMENT '见 API 文档状态码',
  `error_message` text,
  `time` int(11) DEFAULT NULL COMMENT '运行时间 ms',
  `memory` int(11) DEFAULT NULL COMMENT '运行内存 kb',
  `score` int(11) DEFAULT NULL COMMENT 'OI得分',
  `cid` bigint(20) DEFAULT '0' COMMENT '比赛ID',
  `total_case` int(11) DEFAULT '0' COMMENT '测试点总数',
  `judged_case` int(11) DEFAULT '0' COMMENT '已完成测试点数量',
  `current_case` int(11) DEFAULT '0' COMMENT '当前判题测试点序号',
  `cpid` bigint(20) DEFAULT '0' COMMENT '比赛内题目显示ID',
  `tid` bigint(20) DEFAULT '0' COMMENT '训练单ID',
  `hid` bigint(20) DEFAULT '0' COMMENT '作业ID',
  `judger` varchar(50) DEFAULT NULL COMMENT '判题机名称',
  `ip` varchar(50) DEFAULT NULL,
  `is_manual` tinyint(1) DEFAULT '0' COMMENT '是否人工重判',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submit_id` (`submit_id`),
  KEY `idx_uid_problem_id` (`uid`, `problem_id`),
  KEY `idx_cid` (`cid`),
  KEY `idx_status` (`status`),
  KEY `idx_gmt_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码提交记录';

-- 评测样例详情
DROP TABLE IF EXISTS `judge_case`;
CREATE TABLE `judge_case` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submit_id` bigint(20) NOT NULL COMMENT '关联 judge.id',
  `case_id` varchar(20) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `time` int(11) DEFAULT NULL,
  `memory` int(11) DEFAULT NULL,
  `score` int(11) DEFAULT NULL,
  `input_data` varchar(255) DEFAULT NULL,
  `output_data` varchar(255) DEFAULT NULL,
  `user_output` text,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_submit_id` (`submit_id`),
  KEY `idx_submit_case` (`submit_id`, `case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 重判任务表
DROP TABLE IF EXISTS `rejudge_task`;
CREATE TABLE `rejudge_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `problem_id` bigint(20) NOT NULL,
  `range_start` datetime DEFAULT NULL,
  `range_end` datetime DEFAULT NULL,
  `status` varchar(20) DEFAULT 'pending' COMMENT 'pending, processing, finished',
  `total_count` int(11) DEFAULT '0',
  `processed_count` int(11) DEFAULT '0',
  `admin_id` varchar(50) NOT NULL,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台重判任务记录';

-- 远程账号池
DROP TABLE IF EXISTS `remote_judge_account`;
CREATE TABLE `remote_judge_account` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `oj` varchar(20) NOT NULL COMMENT 'Codeforces, POJ, etc.',
  `username` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `status` tinyint(1) DEFAULT '1' COMMENT '1:启用, 0:禁用',
  `max_concurrency` int(11) DEFAULT '1',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ========================================================
-- 4. 比赛数据库: hnieoj_contest_db
-- ========================================================
CREATE DATABASE IF NOT EXISTS `hnieoj_contest_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `hnieoj_contest_db`;

-- 比赛表
DROP TABLE IF EXISTS `contest`;
CREATE TABLE `contest` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` varchar(50) NOT NULL COMMENT '创建者UID',
  `author` varchar(100) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `description` longtext,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `type` int(11) DEFAULT '0' COMMENT '0:ACM, 1:OI',
  `auth` int(11) DEFAULT '0' COMMENT '0:Public, 1:Private',
  `pwd` varchar(255) DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `is_visible` tinyint(1) DEFAULT '1',
  `status` int(11) DEFAULT '0' COMMENT '-1:未开始, 0:进行中, 1:已结束',
  `rank_show_name` varchar(20) DEFAULT 'username',
  `open_rank` tinyint(1) DEFAULT '1',
  `seal_rank` tinyint(1) DEFAULT '0' COMMENT '封榜',
  `seal_rank_time` datetime DEFAULT NULL,
  `custom_tags` json DEFAULT NULL COMMENT '自定义标签',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 比赛题目关联
DROP TABLE IF EXISTS `contest_problem`;
CREATE TABLE `contest_problem` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cid` bigint(20) NOT NULL,
  `problem_id` bigint(20) NOT NULL,
  `display_id` varchar(10) NOT NULL,
  `display_title` varchar(255) DEFAULT NULL,
  `color` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cid_display` (`cid`, `display_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 比赛报名/注册
DROP TABLE IF EXISTS `contest_register`;
CREATE TABLE `contest_register` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cid` bigint(20) NOT NULL,
  `uid` varchar(50) NOT NULL,
  `status` int(11) DEFAULT '1' COMMENT '0:审核中, 1:成功',
  `type` varchar(20) DEFAULT 'user' COMMENT 'user, team',
  `team_id` bigint(20) DEFAULT NULL,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cid_uid` (`cid`, `uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 比赛队伍
DROP TABLE IF EXISTS `contest_team`;
CREATE TABLE `contest_team` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cid` bigint(20) NOT NULL,
  `name` varchar(100) NOT NULL,
  `captain_uid` varchar(50) NOT NULL,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='比赛队伍表';

-- 队伍成员
DROP TABLE IF EXISTS `contest_team_member`;
CREATE TABLE `contest_team_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `team_id` bigint(20) NOT NULL,
  `uid` varchar(50) NOT NULL,
  `is_captain` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_team` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 比赛公告
DROP TABLE IF EXISTS `contest_announcement`;
CREATE TABLE `contest_announcement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cid` bigint(20) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` longtext,
  `uid` varchar(50) NOT NULL COMMENT '发布者',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ========================================================
-- 5. 训练与作业数据库: hnieoj_training_db
-- ========================================================
CREATE DATABASE IF NOT EXISTS `hnieoj_training_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `hnieoj_training_db`;

-- 训练题单 (Training)
DROP TABLE IF EXISTS `training`;
CREATE TABLE `training` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `description` longtext,
  `author` varchar(50) NOT NULL,
  `type` varchar(20) DEFAULT 'Official' COMMENT 'Official, User',
  `auth` varchar(20) DEFAULT 'Public' COMMENT 'Public, Private',
  `private_pwd` varchar(255) DEFAULT NULL,
  `status` tinyint(1) DEFAULT '1',
  `rank` int(11) DEFAULT '0' COMMENT '排序',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 训练分类
DROP TABLE IF EXISTS `training_category`;
CREATE TABLE `training_category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `color` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 训练-分类关联
DROP TABLE IF EXISTS `training_category_rel`;
CREATE TABLE `training_category_rel` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `tid` bigint(20) NOT NULL,
  `cid` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 训练题目
DROP TABLE IF EXISTS `training_problem`;
CREATE TABLE `training_problem` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `tid` bigint(20) NOT NULL,
  `problem_id` bigint(20) NOT NULL,
  `display_id` int(11) DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 作业表 (Homework)
DROP TABLE IF EXISTS `homework`;
CREATE TABLE `homework` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `description` longtext,
  `author` varchar(50) NOT NULL,
  `source` varchar(255) DEFAULT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `status` tinyint(1) DEFAULT '1',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 作业-班级关联 (发布给谁)
DROP TABLE IF EXISTS `homework_class`;
CREATE TABLE `homework_class` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `hid` bigint(20) NOT NULL,
  `class_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_hid` (`hid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 作业题目
DROP TABLE IF EXISTS `homework_problem`;
CREATE TABLE `homework_problem` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `hid` bigint(20) NOT NULL,
  `problem_id` bigint(20) NOT NULL,
  `display_id` int(11) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ========================================================
-- 6. 讨论数据库: hnieoj_discussion_db
-- ========================================================
CREATE DATABASE IF NOT EXISTS `hnieoj_discussion_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `hnieoj_discussion_db`;

-- 讨论主贴
DROP TABLE IF EXISTS `discussion`;
CREATE TABLE `discussion` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `content` longtext NOT NULL,
  `description` varchar(500) DEFAULT NULL COMMENT '摘要',
  `uid` varchar(50) NOT NULL,
  `author` varchar(100) NOT NULL,
  `role` varchar(20) DEFAULT 'user',
  `category` varchar(20) NOT NULL COMMENT 'Site, Problem',
  `problem_code` varchar(50) DEFAULT NULL COMMENT '关联题目展示ID',
  `view_num` int(11) DEFAULT '0',
  `like_num` int(11) DEFAULT '0',
  `top_priority` tinyint(1) DEFAULT '0' COMMENT '置顶',
  `status` int(11) DEFAULT '0' COMMENT '0:正常, 1:关闭',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 回答 (Level 1 Reply)
DROP TABLE IF EXISTS `discussion_answer`;
CREATE TABLE `discussion_answer` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `did` bigint(20) NOT NULL COMMENT '主贴ID',
  `content` longtext NOT NULL,
  `uid` varchar(50) NOT NULL,
  `author` varchar(100) NOT NULL,
  `like_num` int(11) DEFAULT '0',
  `status` int(11) DEFAULT '0',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_did` (`did`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评论 (Level 2 Reply - Comment on Answer)
DROP TABLE IF EXISTS `discussion_comment`;
CREATE TABLE `discussion_comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `aid` bigint(20) NOT NULL COMMENT '回答ID',
  `content` text NOT NULL,
  `uid` varchar(50) NOT NULL,
  `author` varchar(100) NOT NULL,
  `reply_to_uid` varchar(50) DEFAULT NULL,
  `reply_to_name` varchar(100) DEFAULT NULL,
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_aid` (`aid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 点赞记录 (防止重复点赞)
DROP TABLE IF EXISTS `discussion_like`;
CREATE TABLE `discussion_like` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` varchar(50) NOT NULL,
  `target_id` bigint(20) NOT NULL,
  `target_type` varchar(20) NOT NULL COMMENT 'post, answer',
  `direction` varchar(10) NOT NULL COMMENT 'up, down',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uid_target` (`uid`, `target_id`, `target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ========================================================
-- 7. 系统配置与文件数据库: hnieoj_system_db
-- ========================================================
CREATE DATABASE IF NOT EXISTS `hnieoj_system_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `hnieoj_system_db`;

-- 系统配置 (KV存储或单行存储，这里使用单行存储方便API映射)
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `website_name` varchar(255) DEFAULT 'HnieOJ',
  `logo_url` varchar(500) DEFAULT NULL,
  `icp_code` varchar(100) DEFAULT NULL,
  `allow_register` tinyint(1) DEFAULT '1',
  `register_mode` varchar(50) DEFAULT 'EMAIL_SUFFIX',
  `allowed_email_suffixes` json DEFAULT NULL,
  `smtp_host` varchar(100) DEFAULT NULL,
  `smtp_port` int(11) DEFAULT '465',
  `smtp_email` varchar(100) DEFAULT NULL,
  `smtp_password` varchar(255) DEFAULT NULL,
  `smtp_nickname` varchar(100) DEFAULT 'HnieOJ Admin',
  `submission_interval` int(11) DEFAULT '10',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化默认配置
INSERT INTO `sys_config` (`id`) VALUES (1);

-- 公告/新闻 (Admin Announcements)
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `content` longtext NOT NULL,
  `uid` varchar(50) NOT NULL COMMENT '发布者',
  `status` tinyint(1) DEFAULT '1',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文件表
DROP TABLE IF EXISTS `file`;
CREATE TABLE `file` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` varchar(50) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `suffix` varchar(20) DEFAULT NULL,
  `path` varchar(500) NOT NULL,
  `type` varchar(50) DEFAULT 'file' COMMENT 'avatar, problem, proof',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
