package com.hnieacm.discussion.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.discussion.dto.AdminUpdateDiscussionRequest;
import com.hnieacm.discussion.service.DiscussionService;
import com.hnieacm.discussion.vo.AdminDiscussionDetailVo;
import com.hnieacm.discussion.vo.AdminDiscussionListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/02/28
 * @Description: 管理端讨论接口
 */
@Tag(name = "讨论管理模块（管理员）")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/admin/discussions")
@RequiredArgsConstructor
public class AdminDiscussionController {

    private final DiscussionService discussionService;

    @Operation(summary = "获取讨论列表（管理端）")
    @GetMapping
    public Result<PageVo<AdminDiscussionListVo>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 必须大于等于 1") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        return Result.success(discussionService.listAdminDiscussions(page, pageSize, keyword, category, status));
    }

    @Operation(summary = "获取讨论详情（管理端）")
    @GetMapping("/{id}")
    public Result<AdminDiscussionDetailVo> detail(
            @PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long discussionId) {
        // hnieoj-discussion 未注册 SaInterceptor，注解不会生效；此处显式执行角色校验，
        // 确保与网关 /api/admin/** 的 ADMIN/ROOT 规则一致。
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        return Result.success(discussionService.getAdminDiscussionDetail(discussionId));
    }

    @Operation(summary = "编辑讨论")
    @PutMapping("/{id}")
    public Result<Void> update(
            @PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long discussionId,
            @Valid @RequestBody AdminUpdateDiscussionRequest request) {
        discussionService.updateDiscussionByAdmin(discussionId, request);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除讨论")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") @Min(value = 1, message = "id 必须大于等于 1") Long discussionId) {
        discussionService.deleteDiscussionByAdmin(discussionId);
        return Result.success("删除成功", null);
    }
}
