package com.hnieacm.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.hnieacm.auth.dto.InviteCodeCreateRequest;
import com.hnieacm.auth.entity.InviteCode;
import com.hnieacm.auth.service.InviteCodeService;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HnieOJ contributors
 * @Description: Administrator-issued one-time registration invitations.
 */
@RestController
@RequestMapping("/api/admin/invite-codes")
@RequiredArgsConstructor
public class AdminInviteCodeController {

    private final InviteCodeService inviteCodeService;

    @GetMapping
    public Result<List<InviteCode>> list() {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        return Result.success(inviteCodeService.list());
    }

    @PostMapping
    public Result<String> create(@Valid @RequestBody InviteCodeCreateRequest request) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        return Result.success(inviteCodeService.create(request.getExpiresAt(), StpUtil.getLoginIdAsString()));
    }

    @PostMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        StpUtil.checkRoleOr(RoleConstant.ADMIN, RoleConstant.ROOT);
        inviteCodeService.revoke(id);
        return Result.success("邀请码已撤销", null);
    }
}
