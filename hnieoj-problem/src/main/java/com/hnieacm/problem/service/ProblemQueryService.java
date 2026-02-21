package com.hnieacm.problem.service;

import com.hnieacm.common.dto.PageVo;
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

    ProblemDetailVo getProblemDetail(String problemCode);
}
