package com.hnieacm.achievement.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.achievement.service.UserAchievementService;
import com.hnieacm.achievement.vo.UserAchievementVo;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
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

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/15
 * @Description: 用户成就模块
 */
@Tag(name = "用户成就模块")
@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAchievementController {

    private final UserAchievementService userAchievementService;

    @Operation(summary = "获取用户成就列表")
    @GetMapping("/{uid}/achievements")
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
    public Result<PageVo<UserAchievementVo>> list(@PathVariable String uid,
                                                  @RequestParam @Min(value = 1, message = "page 必须>=1") int page,
                                                  @RequestParam @Min(value = 1, message = "pageSize 必须>=1") int pageSize) {
        return Result.success(userAchievementService.listByUid(uid, page, pageSize));
    }
}
