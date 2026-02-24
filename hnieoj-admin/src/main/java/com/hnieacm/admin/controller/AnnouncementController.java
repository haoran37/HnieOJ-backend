package com.hnieacm.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hnieacm.admin.service.AnnouncementService;
import com.hnieacm.admin.vo.AnnouncementDetailVo;
import com.hnieacm.admin.vo.AnnouncementListVo;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
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
 * @Date: 2026/02/24
 * @Description: 前台公告接口
 */
@Tag(name = "公告模块（前台）")
@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "获取公告列表")
    @GetMapping
    public Result<PageVo<AnnouncementListVo>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(announcementService.listPublicAnnouncements(page, pageSize, keyword));
    }

    @Operation(summary = "获取公告详情")
    @GetMapping("/{id}")
    public Result<AnnouncementDetailVo> detail(
            @PathVariable @Min(value = 1, message = "id 必须大于等于 1") Long id) {
        return Result.success(announcementService.getPublicAnnouncementDetail(id));
    }
}
