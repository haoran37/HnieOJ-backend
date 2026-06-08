package com.hnieacm.judge.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.judge.service.RemoteJudgeAccountService;
import com.hnieacm.judge.vo.RemoteJudgeAccountVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/03/01
 * @Description: 判题管理接口（管理员）
 */
@Tag(name = "判题管理模块（管理员）")
@Validated
@RestController
@RequestMapping("/api/admin/judge")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminJudgeController {

    private final RemoteJudgeAccountService remoteJudgeAccountService;

    @Operation(summary = "获取远程评测账号列表")
    @GetMapping("/account")
    public Result<List<RemoteJudgeAccountVo>> listRemoteJudgeAccounts(
            @RequestParam(required = false) String oj,
            @RequestParam(required = false) Integer status) {
        return Result.success(remoteJudgeAccountService.listRemoteJudgeAccounts(oj, status));
    }
}
