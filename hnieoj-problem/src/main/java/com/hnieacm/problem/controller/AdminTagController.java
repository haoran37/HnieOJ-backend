package com.hnieacm.problem.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.problem.dto.SaveTagConfigRequest;
import com.hnieacm.problem.service.ProblemTagConfigService;
import com.hnieacm.problem.vo.TagGroupVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/09
 * @Description: 管理端标签配置接口
 */
@Tag(name = "题目标签管理模块")
@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final ProblemTagConfigService problemTagConfigService;

    @Operation(summary = "获取标签列表")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @GetMapping
    public Result<List<TagGroupVo>> list() {
        return Result.success(problemTagConfigService.list());
    }

    @Operation(summary = "保存标签配置")
    @SaCheckPermission(PermissionConstant.PROBLEM_UPDATE)
    @PutMapping
    public Result<Void> save(@Valid @RequestBody SaveTagConfigRequest request) {
        problemTagConfigService.save(request);
        return Result.success("保存成功", null);
    }
}
