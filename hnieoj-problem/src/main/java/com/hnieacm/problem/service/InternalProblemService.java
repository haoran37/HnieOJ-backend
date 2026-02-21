package com.hnieacm.problem.service;

import com.hnieacm.problem.dto.ProblemBasicDto;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 内部题目查询服务
 */
public interface InternalProblemService {

    ProblemBasicDto getProblemBasicByProblemCode(String problemCode);
}
