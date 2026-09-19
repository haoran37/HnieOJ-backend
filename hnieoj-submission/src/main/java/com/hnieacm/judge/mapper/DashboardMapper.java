package com.hnieacm.judge.mapper;

import com.hnieacm.judge.vo.ActiveTrainingVo;
import com.hnieacm.judge.vo.DashboardStatusCountVo;
import com.hnieacm.judge.vo.PopularProblemVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端 dashboard 聚合查询 Mapper
 */
@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM hnieoj_user_db.user_info")
    Long countTotalUsers();

    @Select("SELECT COUNT(DISTINCT uid) FROM hnieoj_judge_db.judge WHERE gmt_create >= CURDATE()")
    Long countTodayActiveSubmitUsers();

    @Select("SELECT COUNT(*) FROM hnieoj_problem_db.problem")
    Long countTotalProblems();

    @Select("SELECT COUNT(*) FROM hnieoj_judge_db.judge")
    Long countTotalSubmissions();

    @Select("SELECT COUNT(*) FROM hnieoj_judge_db.judge WHERE status = 0")
    Long countAcceptedSubmissions();

    @Select("SELECT COUNT(*) FROM hnieoj_judge_db.judge WHERE status >= 0")
    Long countTerminalSubmissions();

    @Select("SELECT COALESCE(AVG(time), 0) FROM hnieoj_judge_db.judge WHERE status >= 0 AND time IS NOT NULL")
    Double averageJudgeTime();

    @Select("SELECT COUNT(*) FROM hnieoj_judge_db.judge WHERE status IN (-10, -9, -8)")
    Long countJudgingSubmissions();

    @Select("SELECT COUNT(*) FROM hnieoj_judge_db.judge WHERE status IN (6, 7, 8)")
    Long countSystemErrorSubmissions();

    @Select("""
            SELECT status AS status, COUNT(*) AS count
            FROM hnieoj_judge_db.judge
            GROUP BY status
            ORDER BY status
            """)
    List<DashboardStatusCountVo> listJudgeStatusCounts();

    @Select("""
            SELECT id AS id,
                   problem_code AS problemCode,
                   title AS title,
                   submission_count AS submissionCount,
                   accepted_count AS acceptedCount
            FROM hnieoj_problem_db.problem
            ORDER BY submission_count DESC, accepted_count DESC, id ASC
            LIMIT 10
            """)
    List<PopularProblemVo> listPopularProblems();

    @Select("""
            SELECT id AS id,
                   title AS title,
                   author AS author,
                   status AS status,
                   gmt_modified AS gmtModified
            FROM hnieoj_training_db.training
            ORDER BY gmt_modified DESC, id DESC
            LIMIT 10
            """)
    List<ActiveTrainingVo> listActiveTrainings();
}
