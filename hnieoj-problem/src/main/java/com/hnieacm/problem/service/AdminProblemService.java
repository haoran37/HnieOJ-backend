package com.hnieacm.problem.service;

import com.hnieacm.common.dto.PageVo;
import com.hnieacm.problem.dto.AddProblemRequest;
import com.hnieacm.problem.dto.UpdateProblemAuthRequest;
import com.hnieacm.problem.dto.UpdateProblemRequest;
import com.hnieacm.problem.vo.AdminProblemListVo;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 管理员题目管理服务
 */
public interface AdminProblemService {

    PageVo<AdminProblemListVo> listProblems(int page, int pageSize, String keyword, Integer auth);

    void addProblem(AddProblemRequest request);

    void updateProblem(UpdateProblemRequest request);

    void deleteProblem(Long id);

    void updateProblemAuth(UpdateProblemAuthRequest request);
}
