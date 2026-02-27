package com.hnieacm.problem.controller;

import com.hnieacm.common.result.Result;
import com.hnieacm.problem.dto.ProblemBatchQueryRequest;
import com.hnieacm.problem.dto.ProblemBasicDto;
import com.hnieacm.problem.service.InternalProblemService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/20
 * @Description: 提供给其他服务调用的内部接口
 */
@Hidden
@Validated
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

    /**
     * @MethodName queryProblemBasicsByIds
     * @Param request
     * @Description 批量查询题目基础信息
     * @Return @return {@link Result }<{@link List }<{@link ProblemBasicDto }>>
     * @Author HaoRan_Lyu
     * @Date 2026/02/27
     */
    @PostMapping("/basic-info/by-ids")
    public Result<List<ProblemBasicDto>> queryProblemBasicsByIds(@Valid @RequestBody ProblemBatchQueryRequest request) {
        return Result.success(internalProblemService.getProblemBasicsByIds(request.getIds()));
    }
}
