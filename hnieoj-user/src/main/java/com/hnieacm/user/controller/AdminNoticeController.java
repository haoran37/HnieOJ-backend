package com.hnieacm.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.NoticeSaveRequest;
import com.hnieacm.user.service.NoticeAdminService;
import com.hnieacm.user.vo.UserNoticeDetailVo;
import com.hnieacm.user.vo.UserNoticeListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
 * @Date: 2026/09/20
 * @Description: 管理端定向通知接口。
 * <p>
 * hnieoj-user 未注册 SaInterceptor，本控制器在每个方法内显式调用
 * {@code StpUtil.checkRoleOr(ADMIN, ROOT)} 完成服务端 ADMIN/ROOT 校验，与网关 {@code /api/admin/**}
 * 规则形成双层防护。
 */
@Tag(name = "定向通知管理（管理员）")
@Validated
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeAdminService noticeAdminService;

    @Operation(summary = "分页查询通知")
    @GetMapping
    public Result<PageVo<UserNoticeListVo>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        return Result.success(noticeAdminService.listNotices(page, pageSize, keyword, status));
    }

    @Operation(summary = "通知详情（含已保存目标）")
    @GetMapping("/{id}")
    public Result<UserNoticeDetailVo> detail(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        return Result.success(noticeAdminService.getNotice(id));
    }

    @Operation(summary = "创建通知草稿")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody NoticeSaveRequest request) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        String operator = StpUtil.getLoginIdAsString();
        return Result.success("创建成功", noticeAdminService.createNotice(request, operator));
    }

    @Operation(summary = "编辑通知草稿")
    @PutMapping("/{id}")
    public Result<Void> update(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id,
            @Valid @RequestBody NoticeSaveRequest request) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        noticeAdminService.updateNotice(id, request);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除通知管理记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        noticeAdminService.deleteNotice(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "发布通知")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        noticeAdminService.publishNotice(id);
        return Result.success("发布成功", null);
    }
}
