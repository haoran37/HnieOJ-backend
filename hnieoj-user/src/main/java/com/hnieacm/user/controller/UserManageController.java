package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.BatchUidsRequest;
import com.hnieacm.user.dto.CreateUserRequest;
import com.hnieacm.user.dto.RejectUserProfileChangeRequest;
import com.hnieacm.user.dto.TransferUserSubmissionsRequest;
import com.hnieacm.user.dto.UpdateUserIpRestrictionRequest;
import com.hnieacm.user.dto.UpdateUserPasswordRequest;
import com.hnieacm.user.dto.UpdateUserRequest;
import com.hnieacm.user.service.UserIpRestrictionService;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.service.UserProfileChangeService;
import com.hnieacm.user.service.UserSubmissionTransferService;
import com.hnieacm.user.vo.BatchOperationResultVo;
import com.hnieacm.user.vo.CreateUserVo;
import com.hnieacm.user.vo.TransferUserSubmissionsVo;
import com.hnieacm.user.vo.UserProfileChangeApplyVo;
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

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/14
 * @Description: 用户管理（管理员）
 */
@Tag(name = "用户管理模块(管理员)")
@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SaCheckPermission(PermissionConstant.USER_MANAGE)
public class UserManageController {

    private final UserManageService userManageService;
    private final UserIpRestrictionService userIpRestrictionService;
    private final UserSubmissionTransferService userSubmissionTransferService;
    private final UserProfileChangeService userProfileChangeService;

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

    @Operation(summary = "设置用户 IP 限制")
    @PutMapping("/{uid}/ip-restriction")
    public Result<Void> updateIpRestriction(@PathVariable String uid,
                                            @Valid @RequestBody UpdateUserIpRestrictionRequest request) {
        userIpRestrictionService.update(uid, request);
        return Result.success("设置成功", null);
    }

    @Operation(summary = "转移用户提交源码")
    @PostMapping("/{sourceUid}/transfer")
    public Result<TransferUserSubmissionsVo> transferSubmissions(@PathVariable String sourceUid,
                                                                 @Valid @RequestBody TransferUserSubmissionsRequest request) {
        return Result.success("转移成功", userSubmissionTransferService.transfer(sourceUid, request));
    }

    @Operation(summary = "查询用户信息修改申请")
    @GetMapping("/changes")
    public Result<PageVo<UserProfileChangeApplyVo>> listProfileChanges(
            @RequestParam(required = false, defaultValue = "1") @Min(value = 1, message = "page 必须>=1") int page,
            @RequestParam(required = false, defaultValue = "20") @Min(value = 1, message = "pageSize 必须>=1") int pageSize,
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String status) {
        return Result.success(userProfileChangeService.listForAdmin(page, pageSize, uid, status));
    }

    @Operation(summary = "通过用户信息修改申请")
    @PutMapping("/{uid}/changes/approve")
    public Result<Void> approveProfileChange(@PathVariable String uid) {
        userProfileChangeService.approve(uid);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "驳回用户信息修改申请")
    @PutMapping("/{uid}/changes/reject")
    public Result<Void> rejectProfileChange(@PathVariable String uid,
                                            @Valid @RequestBody RejectUserProfileChangeRequest request) {
        userProfileChangeService.reject(uid, request.getReason());
        return Result.success("操作成功", null);
    }

    @Operation(summary = "批量通过用户信息修改申请")
    @PutMapping("/changes/batch-approve")
    public Result<BatchOperationResultVo> batchApproveProfileChanges(@Valid @RequestBody BatchUidsRequest request) {
        return Result.success("操作成功", userProfileChangeService.batchApprove(request));
    }
}

