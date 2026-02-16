package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.service.UserProfileService;
import com.hnieacm.user.vo.UserDetailVo;
import com.hnieacm.user.vo.UserListVo;
import com.hnieacm.user.vo.UserProfileVo;
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
 * @Date: 2026/02/14
 * @Description: User profile and user query APIs (login required).
 */
@Tag(name = "User Profile")
@Validated
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final UserManageService userManageService;

    @Operation(summary = "Get current user profile")
    @SaCheckLogin
    @GetMapping("/profile")
    public Result<UserProfileVo> getProfile() {
        return Result.success(userProfileService.getCurrentUserProfile());
    }

    @Operation(summary = "Get user list (paged)")
    @SaCheckLogin
    @GetMapping("/users")
    public Result<PageVo<UserListVo>> listUsers(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long collegeId,
                                                @RequestParam(required = false) String grade,
                                                @RequestParam(required = false) Long classId,
                                                @RequestParam @Min(value = 1, message = "page must be >= 1") int page,
                                                @RequestParam @Min(value = 1, message = "pageSize must be >= 1") int pageSize) {
        return Result.success(userManageService.listUsers(keyword, collegeId, grade, classId, page, pageSize));
    }

    @Operation(summary = "Get user detail by uid")
    @SaCheckLogin
    @GetMapping("/users/{uid}")
    public Result<UserDetailVo> getUserByUid(@PathVariable String uid) {
        return Result.success(userManageService.getUserDetail(uid));
    }
}
