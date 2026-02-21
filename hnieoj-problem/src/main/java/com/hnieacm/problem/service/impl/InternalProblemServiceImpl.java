package com.hnieacm.problem.service.impl;

import com.hnieacm.problem.dto.ProblemBasicDto;
import com.hnieacm.problem.entity.Problem;
import com.hnieacm.problem.mapper.ProblemMapper;
import com.hnieacm.problem.service.InternalProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/21
 * @Description: 内部题目服务实现
 */
@Service
@RequiredArgsConstructor
public class InternalProblemServiceImpl implements InternalProblemService {

    private final ProblemMapper problemMapper;

    /**
     * @MethodName getProblemBasicByProblemCode
     * @Param problemCode
     * @Description 通过 ProblemCode 获取题目基本信息
     * @Return @return {@link ProblemBasicDto }
     * @Author HaoRan_Lyu
     * @Date 2026/02/21
     */
    @Override
    public ProblemBasicDto getProblemBasicByProblemCode(String problemCode) {
        Problem problem = ProblemServiceSupport.queryProblemByCode(problemMapper, problemCode);

        ProblemBasicDto dto = new ProblemBasicDto();
        dto.setId(problem.getId());
        dto.setProblemCode(problem.getProblemCode());
        dto.setTitle(problem.getTitle());
        dto.setAuth(problem.getAuth());
        dto.setType(problem.getType());
        return dto;
    }
}
