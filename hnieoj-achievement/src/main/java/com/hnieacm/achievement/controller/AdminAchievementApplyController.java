package com.hnieacm.achievement.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.achievement.dto.RejectAchievementApplyRequest;
import com.hnieacm.achievement.service.AchievementApplyService;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证管理（管理员）
 */
@Tag(name = "成就认证管理模块(管理员)")
@RestController
@RequestMapping("/api/admin/achievements")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminAchievementApplyController {

    private final AchievementApplyService achievementApplyService;

    @Operation(summary = "通过成就认证申请")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        achievementApplyService.approve(id);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "驳回成就认证申请")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectAchievementApplyRequest request) {
        achievementApplyService.reject(id, request.getReason());
        return Result.success("操作成功", null);
    }
}

