package com.hnieacm.auth.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.auth.dto.BatchUidsRequest;
import com.hnieacm.auth.dto.RejectRegistrationRequest;
import com.hnieacm.auth.service.RegistrationApplyQueryService;
import com.hnieacm.auth.service.RegistrationReviewService;
import com.hnieacm.auth.vo.RegistrationApplyVo;
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
 * @Description: 注册审核模块
 */
@Tag(name = "注册审核模块")
@Validated
@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class RegistrationReviewController {

    private final RegistrationReviewService registrationReviewService;
    private final RegistrationApplyQueryService registrationApplyQueryService;

    @Operation(summary = "获取注册申请用户列表")
    @GetMapping
    public Result<PageVo<RegistrationApplyVo>> list(@RequestParam @Min(value = 1, message = "page 必须>=1") int page,
                                                    @RequestParam @Min(value = 1, message = "pageSize 必须>=1") int pageSize,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) String keyword) {
        return Result.success(registrationApplyQueryService.list(page, pageSize, status, keyword));
    }

    @Operation(summary = "通过注册申请")
    @PostMapping("/{uid}/approve")
    public Result<Void> approve(@PathVariable String uid) {
        registrationReviewService.approve(uid);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "驳回注册申请")
    @PostMapping("/{uid}/reject")
    public Result<Void> reject(@PathVariable String uid, @Valid @RequestBody RejectRegistrationRequest request) {
        registrationReviewService.reject(uid, request.getReason());
        return Result.success("操作成功", null);
    }

    @Operation(summary = "批量通过注册申请")
    @PostMapping("/batch/approve")
    public Result<String> batchApprove(@Valid @RequestBody BatchUidsRequest request) {
        String summary = registrationReviewService.batchApprove(request.getUids());
        return Result.success("操作成功", summary);
    }
}
