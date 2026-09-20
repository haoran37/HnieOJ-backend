-- ============================================================================
-- 升级脚本：B1 标签/推荐/远程账号/新闻
-- 适用：已按 deploy/mysql/hnieoj_多数据库.sql 初始化并升级过 20260918_redis_gateway.sql 的存量库。
-- 性质：纯增量（additive），不 DROP 任何业务对象/数据、不重写业务数据；脚本可重复执行。
-- 说明：
--   * 不使用 MySQL 不支持的 ADD COLUMN IF NOT EXISTS，统一用 information_schema + 预处理语句判断。
--   * MySQL 8 预处理语句协议不支持 SIGNAL，重复数据预检改用本脚本专用临时存储过程承载
--     条件 SIGNAL；该过程只在预检期间存在，预检通过后立即删除，失败残留也会在下一次重跑时先清理。
--   * 重复数据预检在任意本批 DDL 之前执行：发现重复即以 SQLSTATE 45000 中止，不删除/合并任何数据。
--   * 全新安装请先执行 hnieoj_多数据库.sql（旧 schema），再执行本增量脚本。
--   * 本脚本不自动在生产执行；请在测试/预发库验证后由运维手动执行。
-- ============================================================================

-- ============================================================================
-- 0. 重复数据预检（必须最先执行，早于本批任何 DDL）
--    MySQL 8 的 prepared statement 协议不支持 SIGNAL（ERROR 1295），因此用本脚本
--    专用临时存储过程做条件校验：
--      * 每次执行先 DROP IF EXISTS，成功/失败重跑都能先清理上一次可能残留的同名过程；
--      * CALL 通过后立即 DROP，脚本不留下永久过程；
--      * CALL 触发 SIGNAL 时客户端中止，后续语句（含 DROP）不执行，过程残留但仅做只读校验，
--        不影响生产对象，下次重跑会被 DROP IF EXISTS 清理。
--    重复仅中止本次升级，需人工核对/清理或改重后重跑，脚本不删除任何历史数据。
-- ============================================================================
USE `hnieoj_judge_db`;

DROP PROCEDURE IF EXISTS `b1_precheck_remote_judge_account_duplicates`;
DELIMITER $$
CREATE PROCEDURE `b1_precheck_remote_judge_account_duplicates`()
BEGIN
    DECLARE v_dup_count INT DEFAULT 0;
    SELECT COUNT(*) INTO v_dup_count
    FROM (
        SELECT `oj`, `username`
        FROM `remote_judge_account`
        GROUP BY `oj`, `username`
        HAVING COUNT(*) > 1
    ) AS b1_dup;
    IF v_dup_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'B1 upgrade aborted: duplicate (oj, username) in remote_judge_account. Deduplicate and rerun. No data deleted.';
    END IF;
END$$
DELIMITER ;

CALL `b1_precheck_remote_judge_account_duplicates`();

DROP PROCEDURE IF EXISTS `b1_precheck_remote_judge_account_duplicates`;

-- ============================================================================
-- 1. hnieoj_system_db.announcement 增加 category 分类列
--    历史记录会通过 DEFAULT 'ANNOUNCEMENT' 归入普通公告，不重分类、不丢正文。
-- ============================================================================
USE `hnieoj_system_db`;

SET @b1_announcement_category_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'hnieoj_system_db'
      AND TABLE_NAME = 'announcement'
      AND COLUMN_NAME = 'category'
);
SET @b1_ddl = IF(@b1_announcement_category_exists = 0,
    'ALTER TABLE `announcement` ADD COLUMN `category` varchar(20) NOT NULL DEFAULT ''ANNOUNCEMENT'' COMMENT ''公告分类：ANNOUNCEMENT/NEWS'' AFTER `status`',
    'SELECT 1');
PREPARE b1_stmt FROM @b1_ddl;
EXECUTE b1_stmt;
DEALLOCATE PREPARE b1_stmt;

-- ============================================================================
-- 2. hnieoj_judge_db.remote_judge_account 增加 (oj, username) 唯一索引
--    重复预检已在本脚本第 0 节执行；重复检测与唯一索引使用同一排序规则，结果一致。
-- ============================================================================
USE `hnieoj_judge_db`;

SET @b1_remote_account_uk_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'hnieoj_judge_db'
      AND TABLE_NAME = 'remote_judge_account'
      AND INDEX_NAME = 'uk_oj_username'
);
SET @b1_ddl = IF(@b1_remote_account_uk_exists = 0,
    'ALTER TABLE `remote_judge_account` ADD UNIQUE KEY `uk_oj_username` (`oj`, `username`)',
    'SELECT 1');
PREPARE b1_stmt FROM @b1_ddl;
EXECUTE b1_stmt;
DEALLOCATE PREPARE b1_stmt;

-- ============================================================================
-- 3. hnieoj_problem_db.problem_tag 增加 (tid) 索引
--    用于标签被引用检测与推荐候选的标签重合统计，缩短删除校验/推荐的扫描范围。
-- ============================================================================
USE `hnieoj_problem_db`;

SET @b1_problem_tag_tid_idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'hnieoj_problem_db'
      AND TABLE_NAME = 'problem_tag'
      AND INDEX_NAME = 'idx_tid'
);
SET @b1_ddl = IF(@b1_problem_tag_tid_idx_exists = 0,
    'ALTER TABLE `problem_tag` ADD KEY `idx_tid` (`tid`)',
    'SELECT 1');
PREPARE b1_stmt FROM @b1_ddl;
EXECUTE b1_stmt;
DEALLOCATE PREPARE b1_stmt;
