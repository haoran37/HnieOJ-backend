package com.hnieacm.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.ProfileChangeReviewRequest;
import com.hnieacm.user.service.ProfileChangeService;
import com.hnieacm.user.vo.ProfileChangeVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
 * @Date: 2026/09/20
 * @Description: 管理端身份资料变更审核接口。
 * <p>
 * hnieoj-user 未注册 SaInterceptor，本控制器显式调用
 * {@code StpUtil.checkRoleOr(ADMIN, ROOT)} 完成服务端 ADMIN/ROOT 校验，与网关 {@code /api/admin/**}
 * 规则形成双层防护。
 */
@Tag(name = "身份资料变更审核（管理员）")
@Validated
@RestController
@RequestMapping("/api/admin/profile-change-requests")
@RequiredArgsConstructor
public class AdminProfileChangeController {

    private final ProfileChangeService profileChangeService;

    @Operation(summary = "分页查询变更申请")
    @GetMapping
    public Result<PageVo<ProfileChangeVo>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        return Result.success(profileChangeService.listAdminChangeRequests(page, pageSize, status, keyword));
    }

    @Operation(summary = "审核通过")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id,
            @Valid @RequestBody(required = false) ProfileChangeReviewRequest request) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        String reviewer = StpUtil.getLoginIdAsString();
        profileChangeService.approve(id, request == null ? null : request.getReason(), reviewer);
        return Result.success("审核通过", null);
    }

    @Operation(summary = "审核驳回")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id,
            @Valid @RequestBody ProfileChangeReviewRequest request) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        String reviewer = StpUtil.getLoginIdAsString();
        profileChangeService.reject(id, request.getReason(), reviewer);
        return Result.success("已驳回", null);
    }
}
