package com.hnieacm.achievement.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.achievement.dto.RejectAchievementApplyRequest;
import com.hnieacm.achievement.service.AchievementApplyService;
import com.hnieacm.achievement.vo.AchievementApplyAdminVo;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证管理（管理员）
 */
@Tag(name = "成就认证管理模块(管理员)")
@Validated
@RestController
@RequestMapping("/api/admin/achievements")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminAchievementApplyController {

    private final AchievementApplyService achievementApplyService;

    @Operation(summary = "获取成就认证申请列表")
    @GetMapping
    public Result<PageVo<AchievementApplyAdminVo>> list(@RequestParam @Min(value = 1, message = "page 必须>=1") int page,
                                                        @RequestParam @Min(value = 1, message = "pageSize 必须>=1") int pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(name = "college", required = false) Long collegeId) {
        return Result.success(achievementApplyService.listForAdmin(page, pageSize, keyword, status, collegeId));
    }

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
