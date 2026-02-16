package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.dto.CreateUserRequest;
import com.hnieacm.user.dto.UpdateUserPasswordRequest;
import com.hnieacm.user.dto.UpdateUserRequest;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.vo.CreateUserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户管理（管理员）
 */
@Tag(name = "用户管理模块(管理员)")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SaCheckPermission(PermissionConstant.USER_MANAGE)
public class UserManageController {

    private final UserManageService userManageService;

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<CreateUserVo> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.success("创建成功", userManageService.createUser(request));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{uid}")
    public Result<Void> update(@PathVariable String uid, @Valid @RequestBody UpdateUserRequest request) {
        userManageService.updateUser(uid, request);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "修改用户密码")
    @PutMapping("/{uid}/password")
    public Result<Void> updatePassword(@PathVariable String uid, @Valid @RequestBody UpdateUserPasswordRequest request) {
        userManageService.updateUserPassword(uid, request);
        return Result.success("密码修改成功", null);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{uid}")
    public Result<Void> delete(@PathVariable String uid) {
        userManageService.deleteUser(uid);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "批量禁用用户")
    @PutMapping("/batch/disable")
    public Result<Void> batchDisable(@Valid @RequestBody BatchUidsRequest request) {
        userManageService.batchDisableUsers(request);
        return Result.success("批量禁用成功", null);
    }

    @Operation(summary = "批量启用用户")
    @PutMapping("/batch/enable")
    public Result<Void> batchEnable(@Valid @RequestBody BatchUidsRequest request) {
        userManageService.batchEnableUsers(request);
        return Result.success("批量启用成功", null);
    }

    @Operation(summary = "批量删除用户")
    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@Valid @RequestBody BatchUidsRequest request) {
        userManageService.batchDeleteUsers(request);
        return Result.success("批量删除成功", null);
    }
}

