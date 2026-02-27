package com.hnieacm.achievement.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.achievement.service.AchievementApplyService;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 成就认证申请（用户提交）
 */
@Tag(name = "成就认证申请")
@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementApplyController {

    private final AchievementApplyService achievementApplyService;

    @Operation(summary = "提交成就认证申请")
    @PostMapping("/apply")
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
    public Result<Void> submitApply(@RequestParam("title") String title,
                                    @RequestParam(value = "description", required = false) String description,
                                    @RequestParam("file") MultipartFile file) {
        String uid = StpUtil.getLoginIdAsString();
        achievementApplyService.submitApply(uid, title, description, file);
        return Result.success("申请提交成功", null);
    }
}
