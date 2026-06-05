package com.hnieacm.problem.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.problem.service.ProblemQueryService;
import com.hnieacm.problem.service.ProblemResourceService;
import com.hnieacm.problem.vo.ProblemCheckVo;
import com.hnieacm.problem.vo.ProblemDetailVo;
import com.hnieacm.problem.vo.ProblemListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/20
 * @Description: 题目公开接口
 */
@Tag(name = "题目模块")
@Validated
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemQueryService problemQueryService;
    private final ProblemResourceService problemResourceService;

    @Operation(summary = "获取题目列表")
    @SaCheckLogin
    @GetMapping
    public Result<PageVo<ProblemListVo>> list(@RequestParam @Min(value = 1, message = "page必须大于等于1") int page,
                                              @RequestParam @Min(value = 1, message = "pageSize必须大于等于1") int pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) List<String> tags,
                                              @RequestParam(required = false) Integer difficulty) {
        return Result.success(problemQueryService.listPublicProblems(page, pageSize, keyword, tags, difficulty));
    }

    @Operation(summary = "获取题目详情")
    @SaCheckLogin
    @GetMapping("/{problemCode}")
    public Result<ProblemDetailVo> detail(@PathVariable String problemCode) {
        return Result.success(problemQueryService.getProblemDetail(problemCode));
    }

    @Operation(summary = "获取题面 Markdown")
    @SaCheckLogin
    @GetMapping("/{id}/statement")
    public Result<String> statement(@PathVariable @Min(value = 1, message = "id 必须大于 0") Long id) {
        return Result.success(problemResourceService.getStatement(id));
    }

    @Operation(summary = "检查题目是否存在")
    @SaCheckRole(
            value = {
                    RoleConstant.STUDENT,
                    RoleConstant.TA,
                    RoleConstant.TEACHER,
                    RoleConstant.ADMIN,
                    RoleConstant.ROOT
            },
            mode = SaMode.OR
    )
    @GetMapping("/check")
    public Result<ProblemCheckVo> check(@RequestParam("problemId") @Min(value = 1, message = "problemId 必须大于 0") Long problemId) {
        return Result.success(problemQueryService.checkProblemExists(problemId));
    }
}
