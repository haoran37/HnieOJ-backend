package com.hnieacm.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.common.dto.PageVo;
import com.hnieacm.common.result.Result;
import com.hnieacm.user.dto.ProfileChangeCreateRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 本人身份资料变更申请接口（uid 取自登录态，不能申请他人身份）。
 */
@Tag(name = "身份资料变更申请（本人）")
@Validated
@RestController
@RequestMapping("/api/user/profile-change-requests")
@RequiredArgsConstructor
public class ProfileChangeController {

    private final ProfileChangeService profileChangeService;

    @Operation(summary = "提交身份资料变更申请")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ProfileChangeCreateRequest request) {
        String uid = StpUtil.getLoginIdAsString();
        profileChangeService.createChangeRequest(uid, request);
        return Result.success("提交成功", null);
    }

    @Operation(summary = "本人变更申请列表")
    @GetMapping
    public Result<PageVo<ProfileChangeVo>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 必须大于等于 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 必须大于等于 1")
            @Max(value = 100, message = "pageSize 不能超过 100") int pageSize) {
        String uid = StpUtil.getLoginIdAsString();
        return Result.success(profileChangeService.listMyChangeRequests(uid, page, pageSize));
    }
}
