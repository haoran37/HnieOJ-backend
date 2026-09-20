package com.hnieacm.judge.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.judge.dto.RemoteJudgeAccountCreateRequest;
import com.hnieacm.judge.dto.RemoteJudgeAccountUpdateRequest;
import com.hnieacm.judge.service.RemoteJudgeAccountService;
import com.hnieacm.judge.vo.RemoteJudgeAccountVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 判题管理接口（管理员）。hnieoj-submission 未启用注解拦截，
 * 因此账号 CRUD 在控制器内显式执行 ADMIN/ROOT 角色校验，不依赖失效注解。
 */
@Tag(name = "判题管理模块（管理员）")
@Validated
@RestController
@RequestMapping("/api/admin/judge")
@RequiredArgsConstructor
public class AdminJudgeController {

    private final RemoteJudgeAccountService remoteJudgeAccountService;

    @Operation(summary = "获取远程评测账号列表")
    @GetMapping("/account")
    public Result<List<RemoteJudgeAccountVo>> listRemoteJudgeAccounts(
            @RequestParam(required = false) String oj,
            @RequestParam(required = false) Integer status) {
        checkAdminRole();
        return Result.success(remoteJudgeAccountService.listRemoteJudgeAccounts(oj, status));
    }

    @Operation(summary = "创建远程评测账号")
    @PostMapping("/account")
    public Result<Void> createRemoteJudgeAccount(@Valid @RequestBody RemoteJudgeAccountCreateRequest request) {
        checkAdminRole();
        remoteJudgeAccountService.createRemoteJudgeAccount(request);
        return Result.success("创建成功", null);
    }

    @Operation(summary = "更新远程评测账号")
    @PutMapping("/account/{id}")
    public Result<Void> updateRemoteJudgeAccount(
            @PathVariable @Min(value = 1, message = "id 必须大于 0") Integer id,
            @Valid @RequestBody RemoteJudgeAccountUpdateRequest request) {
        checkAdminRole();
        remoteJudgeAccountService.updateRemoteJudgeAccount(id, request);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除远程评测账号")
    @DeleteMapping("/account/{id}")
    public Result<Void> deleteRemoteJudgeAccount(
            @PathVariable @Min(value = 1, message = "id 必须大于 0") Integer id) {
        checkAdminRole();
        remoteJudgeAccountService.deleteRemoteJudgeAccount(id);
        return Result.success("删除成功", null);
    }

    private void checkAdminRole() {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
    }
}
