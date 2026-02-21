package com.hnieacm.problem.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.problem.dto.AddProblemRequest;
import com.hnieacm.problem.dto.DeleteProblemRequest;
import com.hnieacm.problem.dto.UpdateProblemAuthRequest;
import com.hnieacm.problem.dto.UpdateProblemRequest;
import com.hnieacm.problem.service.AdminProblemService;
import com.hnieacm.problem.vo.AdminProblemListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/20
 * @Description: 题目管理接口
 */
@Tag(name = "题目管理模块")
@Validated
@RestController
@RequestMapping("/api/admin/problem")
@RequiredArgsConstructor
public class AdminProblemController {

    private final AdminProblemService adminProblemService;

    @Operation(summary = "获取题目列表")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @GetMapping("/list")
    public Result<PageVo<AdminProblemListVo>> list(@RequestParam @Min(value = 1, message = "page必须大于等于1") int page,
                                                   @RequestParam @Min(value = 1, message = "pageSize必须大于等于1") int pageSize,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer auth) {
        return Result.success(adminProblemService.listProblems(page, pageSize, keyword, auth));
    }

    @Operation(summary = "添加题目")
    @SaCheckPermission(PermissionConstant.PROBLEM_CREATE)
    @PostMapping
    public Result<Void> add(@Valid @RequestBody AddProblemRequest request) {
        adminProblemService.addProblem(request);
        return Result.success("添加成功", null);
    }

    @Operation(summary = "编辑题目")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PutMapping
    public Result<Void> update(@Valid @RequestBody UpdateProblemRequest request) {
        adminProblemService.updateProblem(request);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除题目")
    @SaCheckPermission(PermissionConstant.PROBLEM_DELETE)
    @DeleteMapping
    public Result<Void> delete(@Valid @RequestBody DeleteProblemRequest request) {
        adminProblemService.deleteProblem(request.getPid());
        return Result.success("删除成功", null);
    }

    @Operation(summary = "切换题目公开状态")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PutMapping("/auth")
    public Result<Void> updateAuth(@Valid @RequestBody UpdateProblemAuthRequest request) {
        adminProblemService.updateProblemAuth(request);
        return Result.success("修改成功", null);
    }
}
