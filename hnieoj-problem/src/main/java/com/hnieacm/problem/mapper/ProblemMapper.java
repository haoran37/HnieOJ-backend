package com.hnieacm.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.problem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: Problem mapper
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 批量推荐候选：仅公开题目（publicAuth），排除源题，按“公共标签重合数降序 + 难度距离升序 + id 升序”排序，
     * 由数据库直接 LIMIT 截断，避免把全库题目拉到 Java 排序。
     * <p>源题没有难度时退化为“标签重合数降序 + id 升序”。所有入参均为参数化占位符，不拼接用户输入。</p>
     *
     * @param sourceId         源题目 DB 主键
     * @param publicAuth       公开题权限值（problem.auth）
     * @param sourceDifficulty 源题难度，可为 null
     * @param limit            最大返回数量
     * @return 已按推荐规则排序并截断的候选题目
     */
    @Select("""
            <script>
            SELECT p.id, p.problem_code, p.title, p.difficulty,
                   p.submission_count, p.accepted_count, p.score_percentage
            FROM problem p
            LEFT JOIN (
                SELECT pt.problem_id AS pid, COUNT(*) AS shared_count
                FROM problem_tag pt
                WHERE pt.tid IN (SELECT src.tid FROM problem_tag src WHERE src.problem_id = #{sourceId})
                GROUP BY pt.problem_id
            ) s ON s.pid = p.id
            WHERE p.auth = #{publicAuth} AND p.id != #{sourceId}
            <choose>
                <when test="sourceDifficulty != null">
                    ORDER BY COALESCE(s.shared_count, 0) DESC,
                             (p.difficulty IS NULL) ASC,
                             ABS(p.difficulty - #{sourceDifficulty}) ASC,
                             p.id ASC
                </when>
                <otherwise>
                    ORDER BY COALESCE(s.shared_count, 0) DESC, p.id ASC
                </otherwise>
            </choose>
            LIMIT #{limit}
            </script>
            """)
    List<Problem> selectRecommendations(@Param("sourceId") Long sourceId,
                                        @Param("publicAuth") int publicAuth,
                                        @Param("sourceDifficulty") Integer sourceDifficulty,
                                        @Param("limit") int limit);
}
