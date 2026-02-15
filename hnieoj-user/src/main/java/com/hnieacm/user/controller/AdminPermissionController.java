package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.dto.GrantPermissionRequest;
import com.hnieacm.user.dto.UpdateUserPermissionRequest;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.vo.PermissionUserVo;
import com.hnieacm.user.vo.UserSearchVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 权限管理
 */
@Tag(name = "权限管理模块")
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SaCheckPermission(PermissionConstant.USER_MANAGE)
public class AdminPermissionController {

    private final UserManageService userManageService;

    @Operation(summary = "获取权限用户列表")
    @GetMapping("/permission/users")
    public Result<PageVo<PermissionUserVo>> getPermissionUsers(@RequestParam @Min(value = 1, message = "page 必须>=1") int page,
                                                               @RequestParam @Min(value = 1, message = "pageSize 必须>=1") int pageSize) {
        return Result.success(userManageService.getPermissionUsers(page, pageSize));
    }

    @Operation(summary = "搜索用户")
    @GetMapping("/users/search")
    public Result<PageVo<UserSearchVo>> searchUsers(@RequestParam @NotBlank(message = "query 不能为空") String query,
                                                    @RequestParam @Min(value = 1, message = "page 必须>=1") int page,
                                                    @RequestParam @Min(value = 1, message = "pageSize 必须>=1") int pageSize) {
        return Result.success(userManageService.searchUsers(query, page, pageSize));
    }

    @Operation(summary = "批量赋予权限")
    @PostMapping("/permission/grant")
    public Result<Void> grant(@Valid @RequestBody GrantPermissionRequest request) {
        userManageService.grantPermissions(request);
        return Result.success("权限添加成功", null);
    }

    @Operation(summary = "更新用户权限")
    @PutMapping("/permission/update")
    public Result<Void> update(@Valid @RequestBody UpdateUserPermissionRequest request) {
        userManageService.updateUserPermission(request);
        return Result.success("权限修改成功", null);
    }

    @Operation(summary = "撤销权限（回归学生）")
    @DeleteMapping("/permission/revoke")
    public Result<Void> revoke(@RequestParam @NotBlank(message = "uid 不能为空") String uid) {
        userManageService.revokePermission(uid);
        return Result.success("权限已删除，用户回归 STUDENT 身份", null);
    }

    @Operation(summary = "批量撤销权限")
    @DeleteMapping("/permission/batch-revoke")
    public Result<Void> batchRevoke(@Valid @RequestBody BatchUidsRequest request) {
        userManageService.batchRevokePermissions(request);
        return Result.success("批量删除成功", null);
    }
}

