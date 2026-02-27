package com.hnieacm.achievement.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.achievement.dto.AddUserAchievementRequest;
import com.hnieacm.achievement.service.UserAchievementService;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/27
 * @Description: 用户成就管理（管理员）
 */
@Tag(name = "用户成就管理模块(管理员)")
@Validated
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminUserAchievementController {

    private final UserAchievementService userAchievementService;

    @Operation(summary = "添加用户成就")
    @PostMapping("/{uid}/achievements")
    public Result<Void> add(@PathVariable String uid, @Valid @RequestBody AddUserAchievementRequest request) {
        userAchievementService.add(uid, request);
        return Result.success("添加成功", null);
    }

    @Operation(summary = "删除用户成就")
    @DeleteMapping("/{uid}/achievements/{achievementId}")
    public Result<Void> delete(@PathVariable String uid, @PathVariable Long achievementId) {
        userAchievementService.delete(uid, achievementId);
        return Result.success("删除成功", null);
    }
}
