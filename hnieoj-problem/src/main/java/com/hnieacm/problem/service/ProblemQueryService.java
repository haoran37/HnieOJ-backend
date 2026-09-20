package com.hnieacm.problem.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.problem.vo.ProblemCheckVo;
import com.hnieacm.problem.vo.ProblemDetailVo;
import com.hnieacm.problem.vo.ProblemListVo;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 公共题目查询服务
 */
public interface ProblemQueryService {

    PageVo<ProblemListVo> listPublicProblems(int page, int pageSize, String keyword, List<String> tags, Integer difficulty);

    /**
     * 推荐题目：源题可见性校验通过后，按公共标签重合/难度接近/稳定 id 排序返回最多 limit 条公开题
     */
    List<ProblemListVo> getRecommendations(String problemCode, int limit);

    ProblemDetailVo getProblemDetail(String problemCode);

    ProblemCheckVo checkProblemExists(Long problemId);
}
