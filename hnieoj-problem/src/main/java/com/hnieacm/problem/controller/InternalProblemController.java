package com.hnieacm.problem.controller;

import com.hnieacm.common.result.Result;
import com.hnieacm.problem.dto.ProblemBasicDto;
import com.hnieacm.problem.service.InternalProblemService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/20
 * @Description: 提供给其他服务调用的内部接口
 */
@Hidden
@RestController
@RequestMapping("/internal/problems")
@RequiredArgsConstructor
public class InternalProblemController {

    private final InternalProblemService internalProblemService;

    /**
     * @MethodName getProblemBasic
     * @Param problemCode
     * @Description 获取题目基础信息
     * @Return @return {@link Result }<{@link ProblemBasicDto }>
     * @Author HaoRan_Lyu
     * @Date 2026/02/20
     */
    @GetMapping("/{problemCode}")
    public Result<ProblemBasicDto> getProblemBasic(@PathVariable String problemCode) {
        return Result.success(internalProblemService.getProblemBasicByProblemCode(problemCode));
    }
}
