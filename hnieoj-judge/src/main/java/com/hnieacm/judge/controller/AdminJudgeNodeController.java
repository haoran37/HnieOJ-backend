package com.hnieacm.judge.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.hnieacm.common.constant.RoleConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.judge.dto.CreateJudgeAuthCodeRequest;
import com.hnieacm.judge.service.FormalJudgeTokenService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeAuthCodeVo;
import com.hnieacm.judge.vo.JudgeFormalTokenVo;
import com.hnieacm.judge.vo.JudgeNodeTokenVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点凭证管理接口
 */
@Tag(name = "判题节点凭证管理模块")
@Validated
@RestController
@RequestMapping("/api/admin/judge/nodes")
@RequiredArgsConstructor
@SaCheckRole(value = {RoleConstant.ADMIN, RoleConstant.ROOT}, mode = SaMode.OR)
public class AdminJudgeNodeController {

    private final JudgeNodeSecurityService judgeNodeSecurityService;
    private final FormalJudgeTokenService formalJudgeTokenService;

    @Operation(summary = "创建临时判题节点授权码")
    @PostMapping("/auth-codes")
    public Result<JudgeAuthCodeVo> createAuthCode(@Valid @RequestBody CreateJudgeAuthCodeRequest request) {
        return Result.success(judgeNodeSecurityService.createAuthCode(request));
    }

    @Operation(summary = "查询判题节点状态")
    @GetMapping
    public Result<List<JudgeNodeTokenVo>> listNodes(@RequestParam(required = false) String status) {
        return Result.success(judgeNodeSecurityService.listTokens(status));
    }

    @Operation(summary = "查询判题节点短期 Token")
    @GetMapping("/tokens")
    public Result<List<JudgeNodeTokenVo>> listTokens(@RequestParam(required = false) String status) {
        return Result.success(judgeNodeSecurityService.listTokens(status));
    }

    @Operation(summary = "吊销判题节点短期 Token")
    @PostMapping("/tokens/{tokenId}/revoke")
    public Result<Void> revokeToken(@PathVariable String tokenId) {
        judgeNodeSecurityService.revokeToken(tokenId);
        return Result.success("吊销成功", null);
    }

    @Operation(summary = "轮换正式判题节点长期 Token")
    @PostMapping("/formal-token/rotate")
    public Result<JudgeFormalTokenVo> rotateFormalToken() {
        return Result.success(formalJudgeTokenService.rotate());
    }
}
