package com.hnieacm.announcement.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.announcement.dto.AnnouncementCreateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateRequest;
import com.hnieacm.announcement.dto.AnnouncementUpdateStatusRequest;
import com.hnieacm.announcement.service.AnnouncementService;
import com.hnieacm.announcement.vo.AnnouncementListVo;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
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
 * @Date: 2026/02/24
 * @Description: 后台公告管理接口
 */
@Tag(name = "公告管理模块（管理员）")
@Validated
@RestController
@RequestMapping("/api/admin/announcements")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "分页查询公告列表")
    @GetMapping
    public Result<PageVo<AnnouncementListVo>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(announcementService.listAdminAnnouncements(page, pageSize, keyword, status));
    }

    @Operation(summary = "创建公告")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AnnouncementCreateRequest request) {
        announcementService.createAnnouncement(request);
        return Result.success("创建成功", null);
    }

    @Operation(summary = "更新公告")
    @PutMapping("/{id}")
    public Result<Void> update(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id,
            @Valid @RequestBody AnnouncementUpdateRequest request) {
        announcementService.updateAnnouncement(id, request);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id) {
        announcementService.deleteAnnouncement(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "更新公告状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id,
            @Valid @RequestBody AnnouncementUpdateStatusRequest request) {
        announcementService.updateAnnouncementStatus(id, request);
        return Result.success("更新成功", null);
    }
}
