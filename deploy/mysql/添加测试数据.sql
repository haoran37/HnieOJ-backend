/*
 * HnieOj Mock Data Script (Fixed & Enhanced)
 * Version: 2.1.0
 * Date: 2026-02-21
 * Description: Populates HnieOJ multi-database schema with rich mock data. 
 * Fixes MySQL 8.0+ reserved keyword (e.g., `rank`) syntax errors.
 * Author: HaoRan Lyu (Enhanced)
 */

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================================
-- 1. 用户与基础数据数据库: hnieoj_user_db
-- ========================================================
USE `hnieoj_user_db`;

-- 学院表
INSERT IGNORE INTO `sys_college` (`id`, `name`) VALUES
(1, '计算机学院'),
(2, '信息工程学院'),
(3, '人工智能学院'),
(4, '软件学院'),
(5, '网络空间安全学院');

-- 班级表
INSERT IGNORE INTO `sys_class` (`id`, `college_id`, `grade`, `name`, `teacher_uid`) VALUES
(1, 1, '2023', '计科1班', '20230002'),
(2, 1, '2023', '计科2班', '20230002'),
(3, 2, '2022', '软工1班', '20230005'),
(4, 3, '2024', '人工智能1班', '20230006'),
(5, 4, '2022', '软件1班', NULL),
(6, 5, '2023', '网安1班', NULL);

-- 班级-助教关联表
INSERT IGNORE INTO `sys_class_ta` (`class_id`, `ta_uid`) VALUES
(1, '20230007'),
(2, '20230007'),
(3, '20230008');

-- 用户表
INSERT INTO `user_info` (`uuid`, `uid`, `username`, `password`, `email`, `phone`, `avatar`, `college_id`, `class_id`, `grade`, `realname`, `qq`, `status`, `cf_username`, `github`, `blog`, `ip_restricted`, `ip_whitelist`) VALUES
('u000001', '20230001', 'admin', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'admin@hnieoj.com', '13800000001', '/avatar/admin.png', 1, 1, '2023', '管理员', '10001', 0, 'admin_cf', 'https://github.com/admin', 'https://blog.admin.com', 0, NULL),
('u000002', '20230002', 'teacher1', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'teacher1@hnieoj.com', '13800000002', '/avatar/teacher1.png', 1, 1, '2023', '张老师', '10002', 0, 'teacher_cf', NULL, NULL, 0, NULL),
('u000003', '20230003', 'student1', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'student1@hnieoj.com', '13800000003', '/avatar/student1.png', 1, 1, '2023', '李同学', '10003', 0, 'student1_cf', 'https://github.com/stu1', NULL, 0, NULL),
('u000004', '20230004', 'student2', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'student2@hnieoj.com', '13800000004', '/avatar/student2.png', 2, 3, '2022', '王同学', '10004', 0, NULL, NULL, NULL, 0, NULL),
('u000005', '20230005', 'teacher2', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'teacher2@hnieoj.com', '13800000005', NULL, 2, 3, '2022', '赵老师', '10005', 0, 'teacher2_cf', NULL, NULL, 0, NULL),
('u000006', '20230006', 'teacher3', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'teacher3@hnieoj.com', '13800000006', NULL, 3, 4, '2024', '孙老师', '10006', 0, NULL, NULL, NULL, 0, NULL),
('u000007', '20230007', 'ta1', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'ta1@hnieoj.com', '13800000007', NULL, 1, 1, '2023', '周助教', '10007', 0, NULL, NULL, NULL, 0, NULL),
('u000008', '20230008', 'ta2', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'ta2@hnieoj.com', '13800000008', NULL, 2, 3, '2022', '吴助教', '10008', 0, NULL, NULL, NULL, 0, NULL),
('u000009', '20230009', 'student3', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'student3@hnieoj.com', '13800000009', NULL, 1, 2, '2023', '郑同学', '10009', 0, 'student3_cf', NULL, NULL, 0, NULL),
('u000010', '20230010', 'student4', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'student4@hnieoj.com', '13800000010', NULL, 3, 4, '2024', '陈同学', '10010', 0, NULL, NULL, 'https://chen.dev', 0, NULL),
('u000011', '20230011', 'student5', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'student5@hnieoj.com', '13800000011', NULL, 4, 5, '2022', '林同学', '10011', 0, NULL, NULL, NULL, 0, NULL),
('u000012', '20230012', 'student6', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'student6@hnieoj.com', '13800000012', NULL, 5, 6, '2023', '黄同学', '10012', 0, NULL, NULL, NULL, 0, NULL);

-- 用户-角色关联表 (1000: root, 1001: admin, 1002: teacher, 1003: ta, 1004: student)
INSERT INTO `user_role` (`user_uid`, `role_id`) VALUES
('20230001', 1000), 
('20230002', 1002), 
('20230003', 1004), 
('20230004', 1004), 
('20230005', 1002), 
('20230006', 1002), 
('20230007', 1003), 
('20230008', 1003), 
('20230009', 1004), 
('20230010', 1004), 
('20230011', 1004), 
('20230012', 1004); 

-- 用户成就表
INSERT INTO `user_achievement` (`uid`, `title`, `content`, `proof_url`, `achieve_time`, `status`) VALUES
('20230003', '蓝桥杯省赛一等奖', '获奖证书编号123', '/proof/lanqiao_20230003.pdf', '2025-05-01', 1),
('20230004', '校级程序设计比赛一等奖', '比赛说明', '/proof/school_20230004.png', '2025-04-15', 1),
('20230009', 'ACM区域赛铜奖', '团队奖项', '/proof/acm_20230009.jpg', '2025-11-20', 1),
('20230010', 'CCSP华南赛区二等奖', '证书扫描件', '/proof/ccsp_20230010.pdf', '2025-12-10', 0); 

-- 成就申请审核表
INSERT INTO `achievement_apply` (`uid`, `title`, `description`, `file_url`, `status`, `reason`) VALUES
('20230004', '校级程序设计比赛一等奖', '2025年校级程序设计竞赛', '/proof/apply_20230004_1.png', 'approved', NULL),
('20230011', '软件设计大赛一等奖', '2025年软件设计大赛', '/proof/apply_20230011_1.jpg', 'pending', NULL),
('20230012', '优秀志愿者', '志愿活动证明', '/proof/apply_20230012_1.pdf', 'rejected', '证明材料不清晰，请重新上传扫描件');

-- 用户注册申请表
INSERT INTO `user_register_apply` (`uid`, `username`, `password`, `email`, `college_id`, `class_id`, `grade`, `qq`, `status`, `reply_info`) VALUES
('20239999', 'new_user', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'new@hnieoj.com', 1, 1, '2023', '123456789', 0, NULL),
('20239888', 'waiting_user', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'waiting@hnieoj.com', 2, 3, '2022', '987654321', 0, NULL),
('20239777', 'approved_user', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'approved@hnieoj.com', 3, 4, '2024', '11223344', 1, NULL),
('20239666', 'rejected_user', '$2a$10$3U2N.7iadlzem5xnKCcDFOX6U/6bX7.2ReqCLHMfFQZSDW3DDMFQC', 'rejected@hnieoj.com', 4, 5, '2022', '55667788', 2, '学号与系统登记不符，请核实。');

-- ========================================================
-- 2. 题目数据库: hnieoj_problem_db
-- ========================================================
USE `hnieoj_problem_db`;

-- 题目表 (新增了 SPJ 和交互题样例)
INSERT INTO `problem` (`id`, `problem_code`, `title`, `author`, `type`, `judge_mode`, `time_limit`, `memory_limit`, `stack_limit`, `description`, `input`, `output`, `examples`, `hint`, `difficulty`, `auth`, `io_score`, `is_remote`, `source`, `spj_code`, `spj_language`, `is_remove_end_blank`, `open_case_result`, `score_percentage`, `submission_count`, `accepted_count`, `data_version`, `modified_user`) VALUES
(1, 'P1000', 'A+B Problem', 'admin', 0, 'default', 1000, 256, 128, '计算两个整数之和', '输入两个整数a和b，用空格分隔', '输出a+b的值', '[{"in": "1 2", "out": "3"}]', '注意整数范围，可能会超过 32 位整型。', 0, 1, 100, 0, 'HnieOJ', NULL, NULL, 1, 1, 0.00, 50, 40, 1, 'admin'),
(2, 'P1001', '排序', 'admin', 0, 'default', 2000, 256, 128, '给定N个数，将它们从小到大排序', '第一行一个整数N，第二行N个整数', '排序后的N个数，空格分隔', '[{"in": "5\\n3 1 4 2 5", "out": "1 2 3 4 5"}]', '建议使用快速排序 (Quick Sort) 或归并排序 (Merge Sort)。', 1, 1, 100, 0, 'HnieOJ', NULL, NULL, 1, 1, 0.00, 30, 25, 1, 'admin'),
(3, 'P1002', '斐波那契数列', 'teacher1', 1, 'default', 1000, 128, 64, '计算第n项斐波那契数', '输入一个整数n', '输出F(n)的值', '[{"in": "5", "out": "5"}]', 'n不超过30', 0, 1, 100, 0, '经典递归', NULL, NULL, 1, 1, 0.00, 20, 18, 1, 'teacher1'),
(4, 'P1003', '最大子段和', 'admin', 0, 'default', 1000, 256, 128, '求给定数组的最大连续子段和', '第一行N，第二行N个整数', '最大子段和', '[{"in": "5\\n-2 1 -3 4 -1 2 1 -5 4", "out": "6"}]', '可以使用 Kadane 算法动态规划解决。', 2, 1, 100, 0, 'LeetCode', NULL, NULL, 1, 0, 0.00, 15, 8, 1, 'admin'),
(5, 'P1004', '字符串反转', 'teacher2', 0, 'default', 500, 64, 32, '输入一个字符串，输出其反转', '一个字符串S', '反转后的字符串', '[{"in": "hello", "out": "olleh"}]', '无', 0, 1, 100, 0, 'HnieOJ', NULL, NULL, 1, 1, 0.00, 25, 23, 1, 'teacher2'),
(6, 'P1005', '素数判断', 'admin', 0, 'default', 1000, 128, 64, '判断一个数是否为素数', '一个整数n', 'Yes 或 No', '[{"in": "7", "out": "Yes"}, {"in": "10", "out": "No"}]', '2 <= n <= 10^9', 1, 1, 100, 0, '数学', NULL, NULL, 1, 1, 0.00, 40, 30, 1, 'admin'),
(7, 'P1006', '任何符合条件的排列 (SPJ)', 'admin', 0, 'spj', 1000, 256, 128, '给定 N，输出任意一个包含 1 到 N 的排列，要求相邻元素互质。', '输入 N (1<=N<=100)', '输出你的排列，空格隔开', '[{"in": "3", "out": "1 2 3"}]', '答案不唯一，本题采用 Special Judge。', 1, 1, 100, 0, 'HnieOJ', '#include<stdio.h>\n// 简单的特判逻辑...', 'C', 1, 1, 0.00, 10, 5, 1, 'admin');

-- 题目标签
INSERT INTO `tag` (`id`, `name`, `color`, `category`) VALUES
(1, '模拟', '#409EFF', '算法'),
(2, '排序', '#67C23A', '算法'),
(3, '数学', '#E6A23C', '算法'),
(4, '动态规划', '#F56C6C', '算法'),
(5, '字符串', '#909399', '算法'),
(6, '数论', '#FF9800', '数学'),
(7, '构造', '#9C27B0', '算法');

-- 题目-标签关联
INSERT INTO `problem_tag` (`problem_id`, `tid`) VALUES
(1, 1), (2, 2), (3, 3), (4, 4), (5, 5), (6, 3), (6, 6), (7, 7);

-- 语言配置
INSERT INTO `language` (`id`, `name`, `content_type`, `compile_command`, `is_spj`) VALUES
(1, 'C++17', 'cpp', 'g++ main.cpp -std=c++17 -o main', 0),
(2, 'Java', 'java', 'javac Main.java', 0),
(3, 'Python3', 'python3', 'python3 main.py', 0),
(4, 'C', 'c', 'gcc main.c -o main', 0),
(5, 'C++11', 'cpp', 'g++ main.cpp -std=c++11 -o main', 0),
(6, 'SPJ_C', 'c', 'gcc spj.c -o spj', 1);

-- ========================================================
-- 3. 判题数据库: hnieoj_judge_db
-- ========================================================
USE `hnieoj_judge_db`;

-- 判题服务器
INSERT INTO `judge_server` (`id`, `name`, `ip`, `port`, `url`, `cpu_core`, `task_number`, `max_task_number`, `status`, `is_remote`) VALUES
(1, 'judge-1', '127.0.0.1', 8080, 'http://127.0.0.1:8080', 8, 0, 4, 0, 0),
(2, 'judge-2', '192.168.1.101', 8080, 'http://192.168.1.101:8080', 16, 2, 8, 0, 0),
(3, 'remote-judge-1', '10.0.0.1', 80, 'http://10.0.0.1', 4, 0, 2, 0, 1);

-- 提交记录 (cid, cpid 等外键关联均设置为安全值)
INSERT INTO `judge` (`id`, `submit_id`, `judge_task_id`, `problem_id`, `problem_code`, `uid`, `username`, `language`, `code`, `status`, `error_message`, `time`, `memory`, `score`, `cid`, `total_case`, `judged_case`, `current_case`, `cpid`, `tid`, `hid`, `judger`, `ip`, `is_manual`) VALUES
(1, 's0001', 'task-s0001', 1, 'P1000', '20230003', 'student1', 'C++17', '#include <iostream>\nint main() { int a,b; std::cin>>a>>b; std::cout<<a+b; return 0; }', 0, NULL, 10, 1024, 100, 0, 1, 1, 1, 0, 0, 0, 'judge-1', '127.0.0.1', 0),
(2, 's0002', 'task-s0002', 2, 'P1001', '20230004', 'student2', 'Python3', 'n=int(input())\na=list(map(int,input().split()))\na.sort()\nprint(*a)', 1, 'Runtime Error', 50, 2048, 80, 0, 2, 2, 2, 0, 0, 0, 'judge-1', '192.168.1.2', 0),
(3, 's0003', 'task-s0003', 1, 'P1000', '20230009', 'student3', 'Java', 'import java.util.*;\npublic class Main{public static void main(String[] args){Scanner sc=new Scanner(System.in);System.out.println(sc.nextInt()+sc.nextInt());}}', 0, NULL, 15, 2048, 100, 0, 1, 1, 1, 0, 0, 0, 'judge-2', '10.0.0.5', 0),
(4, 's0004', 'task-s0004', 3, 'P1002', '20230010', 'student4', 'C', '#include <stdio.h>\nint main() { int n; scanf("%d",&n); printf("%d",fib(n)); }', 2, 'Compile Error: function fib not declared.', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, 0, 'judge-1', '10.0.0.6', 0),
(5, 's0005', 'task-s0005', 4, 'P1003', '20230011', 'student5', 'C++17', '...', -10, NULL, NULL, NULL, NULL, 0, 10, 0, 0, 0, 0, 0, NULL, NULL, 0),
(6, 's0006', 'task-s0006', 5, 'P1004', '20230012', 'student6', 'Python3', 'print(input()[::-1])', 0, NULL, 5, 512, 100, 0, 1, 1, 1, 0, 0, 0, 'judge-2', '192.168.1.3', 0),
(7, 's0007', 'task-s0007', 2, 'P1001', '20230003', 'student1', 'C++17', '...', 3, 'Wrong Answer', 8, 1024, 50, 1, 2, 2, 2, 1, 0, 0, 'judge-1', '127.0.0.1', 0),
(8, 's0008', 'task-s0008', 7, 'P1006', '20230003', 'student1', 'C++17', '#include <iostream>\nint main(){std::cout<<"1 2 3";}', -8, NULL, NULL, NULL, NULL, 0, 10, 2, 3, 0, 0, 0, 'judge-1', '127.0.0.1', 0),
(9, 's0009', 'task-s0009', 1, 'P1000', '20230003', 'student1', 'C++17', '#include <iostream>\nint main(){return 0;}', -9, NULL, NULL, NULL, NULL, 0, 10, 0, 0, 0, 0, 0, 'judge-1', '127.0.0.1', 0);

-- 评测样例详情
INSERT INTO `judge_case` (`submit_id`, `case_id`, `status`, `time`, `memory`, `score`, `input_data`, `output_data`, `user_output`) VALUES
(1, '1', 0, 10, 512, 100, '1 2', '3', '3'),
(2, '1', 1, 50, 2048, 80, '5\n3 1 4 2 5', '1 2 3 4 5', '1 2 3 4 5'),
(2, '2', 3, 0, 0, 0, '3\n3 2 1', '1 2 3', '1 2'),
(3, '1', 0, 15, 1024, 100, '2 3', '5', '5'),
(6, '1', 0, 5, 256, 100, 'hello', 'olleh', 'olleh'),
(7, '1', 3, 8, 1024, 50, '5\n3 1 4 2 5', '1 2 3 4 5', '1 3 2 4 5');

-- 重判任务表
INSERT INTO `rejudge_task` (`id`, `problem_id`, `problem_code`, `contest_id`, `range_start`, `range_end`, `status`, `total_count`, `processed_count`, `failed_count`, `last_judge_id`, `last_error`, `admin_id`) VALUES
(1, 1, 'P1000', NULL, '2026-02-01 00:00:00', '2026-02-20 23:59:59', 'finished', 10, 10, 0, 10, NULL, '20230001'),
(2, 2, 'P1001', NULL, NULL, NULL, 'pending', 20, 0, 0, 0, NULL, '20230001'),
(3, 3, 'P1002', NULL, NULL, NULL, 'processing', 5, 2, 0, 2, NULL, '20230002');

-- 远程账号池
INSERT INTO `remote_judge_account` (`id`, `oj`, `username`, `password`, `status`, `max_concurrency`) VALUES
(1, 'Codeforces', 'cf_account1', 'mockpwd1', 1, 2),
(2, 'Codeforces', 'cf_account2', 'mockpwd2', 1, 2),
(3, 'POJ', 'poj_account', 'mockpwd_poj', 1, 1),
(4, 'AtCoder', 'atc_account', 'mockpwd_atc', 0, 1);

-- ========================================================
-- 4. 比赛数据库: hnieoj_contest_db
-- ========================================================
USE `hnieoj_contest_db`;

-- 比赛表
INSERT INTO `contest` (`id`, `uid`, `author`, `title`, `description`, `start_time`, `end_time`, `type`, `auth`, `pwd`, `source`, `is_visible`, `status`, `rank_show_name`, `open_rank`, `seal_rank`, `seal_rank_time`, `custom_tags`) VALUES
(1, '20230001', 'admin', '2026春季校赛', '湖南工程学院2026年春季程序设计竞赛', '2026-03-01 09:00:00', '2026-03-01 12:00:00', 0, 0, NULL, 'HnieOJ', 1, -1, 'username', 1, 0, NULL, NULL),
(2, '20230002', 'teacher1', '计科1班练习赛', '班级内部练习', '2026-02-25 14:00:00', '2026-02-25 17:00:00', 1, 1, 'abc123', 'teacher', 1, 0, 'realname', 1, 1, '2026-02-25 16:00:00', '["班级赛", "练习"]'),
(3, '20230005', 'teacher2', '新生热身赛', '面向大一新生的热身赛', '2026-02-10 10:00:00', '2026-02-10 12:00:00', 0, 0, NULL, 'HnieOJ', 1, 1, 'username', 1, 0, NULL, NULL);

-- 比赛题目关联
INSERT INTO `contest_problem` (`id`, `cid`, `problem_id`, `display_id`, `display_title`, `color`) VALUES
(1, 1, 1, 'A', 'A+B Problem', '#409EFF'),
(2, 1, 2, 'B', '排序', '#67C23A'),
(3, 1, 3, 'C', '斐波那契数列', '#E6A23C'),
(4, 2, 1, 'A', 'A+B Problem', '#409EFF'),
(5, 2, 5, 'B', '字符串反转', '#909399'),
(6, 3, 5, 'A', '字符串反转', '#909399'),
(7, 3, 6, 'B', '素数判断', '#FF9800');

-- 比赛报名/注册
INSERT INTO `contest_register` (`cid`, `uid`, `status`, `type`, `team_id`) VALUES
(1, '20230003', 1, 'user', NULL),
(1, '20230004', 1, 'user', NULL),
(1, '20230009', 1, 'user', NULL),
(1, '20230010', 1, 'team', 1),
(1, '20230011', 1, 'team', 1),
(2, '20230003', 1, 'user', NULL),
(2, '20230009', 1, 'user', NULL),
(3, '20230003', 1, 'user', NULL),
(3, '20230010', 1, 'user', NULL),
(3, '20230011', 1, 'user', NULL),
(3, '20230012', 1, 'user', NULL);

-- 比赛队伍
INSERT INTO `contest_team` (`id`, `cid`, `name`, `captain_uid`) VALUES
(1, 1, 'Team Alpha', '20230010'),
(2, 1, 'Team Beta', '20230011');

-- 队伍成员
INSERT INTO `contest_team_member` (`team_id`, `uid`, `is_captain`) VALUES
(1, '20230010', 1),
(1, '20230011', 0),
(2, '20230011', 1),
(2, '20230012', 0);

-- 比赛公告
INSERT INTO `contest_announcement` (`cid`, `title`, `content`, `uid`) VALUES
(1, '比赛说明', '请遵守比赛规则，禁止任何形式的代码抄袭。', '20230001'),
(1, '温馨提示', '比赛过程中如有问题请在 Clarification 中提问。', '20230001'),
(2, '练习赛说明', '本次练习赛不计入最终成绩，仅作为查漏补缺的参考。', '20230002');

-- ========================================================
-- 5. 训练与作业数据库: hnieoj_training_db
-- ========================================================
USE `hnieoj_training_db`;

-- 训练题单 (!!! 此处修复了 rank 关键字冲突问题 !!!)
INSERT INTO `training` (`id`, `title`, `description`, `author`, `type`, `auth`, `private_pwd`, `status`, `rank`) VALUES
(1, '入门训练', '基础算法练习，适合初学者', '20230002', 'Official', 'Public', NULL, 1, 1),
(2, '动态规划专项', '动态规划经典题目', '20230001', 'Official', 'Public', NULL, 1, 2),
(3, '数学专题', '数论、组合数学等', '20230002', 'Official', 'Private', 'math123', 1, 3),
(4, '蓝桥杯真题', '历年蓝桥杯真题精选', '20230001', 'Official', 'Public', NULL, 1, 4);

-- 训练分类
INSERT INTO `training_category` (`id`, `name`, `color`) VALUES
(1, '基础', '#409EFF'),
(2, '动态规划', '#F56C6C'),
(3, '数学', '#E6A23C'),
(4, '字符串', '#909399'),
(5, '搜索', '#67C23A');

-- 训练-分类关联
INSERT INTO `training_category_rel` (`tid`, `cid`) VALUES
(1, 1), (2, 2), (3, 3), (4, 1), (4, 2);

-- 训练题目
INSERT INTO `training_problem` (`tid`, `problem_id`, `display_id`) VALUES
(1, 1, 1), (1, 5, 2), (1, 6, 3), 
(2, 4, 1), (2, 3, 2), 
(3, 6, 1), (3, 3, 2), 
(4, 1, 1), (4, 2, 2), (4, 4, 3);

-- 作业表
INSERT INTO `homework` (`id`, `title`, `description`, `author`, `source`, `start_time`, `end_time`, `status`) VALUES
(1, '第一次作业', '完成基础题目', '20230002', 'teacher1', '2026-02-15 00:00:00', '2026-02-20 23:59:59', 1),
(2, '第二次作业', '动态规划练习', '20230002', 'teacher1', '2026-02-25 00:00:00', '2026-03-05 23:59:59', 1),
(3, '实验报告', '排序算法实验', '20230005', 'teacher2', '2026-02-10 00:00:00', '2026-02-28 23:59:59', 1);

-- 作业-班级关联
INSERT INTO `homework_class` (`hid`, `class_id`) VALUES
(1, 1), (1, 2), (2, 1), (2, 2), (3, 3);

-- 作业题目
INSERT INTO `homework_problem` (`hid`, `problem_id`, `display_id`) VALUES
(1, 1, 1), (1, 5, 2), 
(2, 4, 1), (2, 3, 2), 
(3, 2, 1);

-- ========================================================
-- 6. 讨论数据库: hnieoj_discussion_db
-- ========================================================
USE `hnieoj_discussion_db`;

-- 讨论主贴
INSERT INTO `discussion` (`id`, `title`, `content`, `description`, `uid`, `author`, `role`, `category`, `problem_code`, `view_num`, `like_num`, `top_priority`, `status`) VALUES
(1, '如何优化排序？', '请问快速排序怎么优化？比如处理大量重复元素时。', '快速排序优化讨论', '20230003', 'student1', 'user', 'Problem', 'P1001', 120, 5, 0, 0),
(2, '动态规划状态定义', '在做最大子段和时，状态转移方程怎么理解？', NULL, '20230009', 'student3', 'user', 'Problem', 'P1003', 45, 2, 0, 0),
(3, '欢迎新同学', '大家有问题可以在讨论区提问，禁止刷屏。', '新生引导', '20230001', 'admin', 'admin', 'Site', NULL, 300, 20, 1, 0),
(4, 'Codeforces 账号绑定', '如何绑定自己的 CF 账号以便同步数据？', NULL, '20230004', 'student2', 'user', 'Site', NULL, 80, 3, 0, 0),
(5, '关于C++17的语法', 'C++17有哪些新特性值得注意？', NULL, '20230010', 'student4', 'user', 'Site', NULL, 60, 1, 0, 1),
(6, 'SPJ 题目怎么写', '发现新增了 SPJ 的题目，请问输出格式有什么特别要求吗？', '关于特判', '20230011', 'student5', 'user', 'Problem', 'P1006', 22, 1, 0, 0);

-- 回答
INSERT INTO `discussion_answer` (`id`, `did`, `content`, `uid`, `author`, `like_num`, `status`) VALUES
(1, 1, '可以使用三路快排，将等于pivot的元素放在中间，减少递归深度。', '20230002', 'teacher1', 10, 0),
(2, 1, '也可以随机选择pivot，避免最坏情况。', '20230007', 'ta1', 3, 0),
(3, 2, 'dp[i]表示以第i个元素结尾的最大子段和，转移方程dp[i]=max(dp[i-1]+a[i], a[i])', '20230002', 'teacher1', 8, 0),
(4, 3, '欢迎新同学！有问题随时提问。', '20230005', 'teacher2', 5, 0),
(5, 4, '在个人设置页面，找到Codeforces账号栏，输入你的CF用户名即可。', '20230001', 'admin', 7, 0),
(6, 6, '只要你的输出符合题目要求的规则，多余的空格换行通常系统 SPJ 会自动忽略，但请仔细阅读提示。', '20230001', 'admin', 2, 0);

-- 评论
INSERT INTO `discussion_comment` (`aid`, `content`, `uid`, `author`, `reply_to_uid`, `reply_to_name`) VALUES
(1, '感谢解答！', '20230003', 'student1', NULL, NULL),
(1, '三路快排确实有效', '20230009', 'student3', NULL, NULL),
(2, '随机化也很重要', '20230003', 'student1', NULL, NULL),
(3, '明白了，谢谢老师', '20230009', 'student3', NULL, NULL),
(4, '欢迎欢迎', '20230011', 'student5', NULL, NULL),
(5, '绑定后有什么好处？', '20230012', 'student6', NULL, NULL),
(5, '可以同步你的CF提交记录，参与校内榜单排行', '20230001', 'admin', '20230012', 'student6');

-- 点赞记录
INSERT INTO `discussion_like` (`uid`, `target_id`, `target_type`, `direction`) VALUES
('20230003', 1, 'post', 'up'),
('20230004', 1, 'post', 'up'),
('20230009', 1, 'answer', 'up'),
('20230010', 1, 'answer', 'up'),
('20230003', 2, 'answer', 'up'),
('20230001', 3, 'post', 'up'),
('20230002', 3, 'post', 'up'),
('20230003', 3, 'post', 'up'),
('20230004', 3, 'post', 'up');

-- ========================================================
-- 7. 系统配置与文件数据库: hnieoj_system_db
-- ========================================================
USE `hnieoj_system_db`;

-- 更新系统配置 (确保 sys_config 中存在 id=1 的记录，这部分在建库 SQL 已初始化)
UPDATE `sys_config` SET
`website_name` = 'HnieOJ 在线评测系统',
`logo_url` = '/logo.png',
`icp_code` = '湘ICP备2026xxxx号',
`allow_register` = 1,
`register_mode` = 'EMAIL_SUFFIX',
`allowed_email_suffixes` = '["@hnie.edu.cn", "@stu.hnie.edu.cn"]',
`smtp_host` = 'smtp.qq.com',
`smtp_port` = 465,
`smtp_email` = 'noreply@hnieoj.com',
`smtp_password` = 'encrypted_smtp_password',
`smtp_nickname` = 'HnieOJ 系统邮件',
`submission_interval` = 10
WHERE `id` = 1;

-- 公告/新闻
INSERT INTO `announcement` (`id`, `title`, `content`, `uid`, `status`) VALUES
(1, '系统上线公告', 'HnieOJ v2.2 微服务版正式上线！欢迎各位师生使用。', '20230001', 1),
(2, '关于举办2026春季校赛的通知', '2026春季校赛将于3月1日举行，请提前组队报名。', '20230001', 1),
(3, '系统维护通知', '2月28日凌晨2:00-4:00进行评测机扩容维护，届时将短暂暂停评测服务。', '20230001', 1),
(4, '新功能上线', '讨论区已新增点赞及二级评论功能，快来体验吧！', '20230002', 1);

-- 文件表
INSERT INTO `file` (`id`, `uid`, `name`, `suffix`, `path`, `type`) VALUES
(1, '20230003', 'avatar1.png', 'png', '/upload/avatar/20230003.png', 'avatar'),
(2, '20230004', 'avatar2.jpg', 'jpg', '/upload/avatar/20230004.jpg', 'avatar'),
(3, '20230003', 'proof_lanqiao.pdf', 'pdf', '/upload/proof/20230003_lanqiao.pdf', 'proof'),
(4, '20230004', 'school_contest.png', 'png', '/upload/proof/20230004_school.png', 'proof'),
(5, '20230001', 'announcement_cover.jpg', 'jpg', '/upload/announce/1.jpg', 'announce'),
(6, '20230009', 'acm_certificate.jpg', 'jpg', '/upload/proof/20230009_acm.jpg', 'proof');

SET FOREIGN_KEY_CHECKS = 1;
