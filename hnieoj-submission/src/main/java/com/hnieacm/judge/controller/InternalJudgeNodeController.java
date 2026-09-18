package com.hnieacm.judge.controller;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.judge.dto.JudgeTaskAccessRequest;
import com.hnieacm.judge.dto.ValidateJudgeNodeTokenRequest;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
import com.hnieacm.judge.vo.JudgeNodeTokenValidationVo;
import com.hnieacm.submission.service.JudgeNodeAccessService;
import com.hnieacm.submission.service.impl.JudgeTaskLeaseManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/05/12
 * @Description: 判题节点内部校验接口
 */
@Tag(name = "判题节点内部校验模块")
@Validated
@RestController
@RequestMapping("/internal/judge")
@RequiredArgsConstructor
public class InternalJudgeNodeController {

    private final JudgeNodeSecurityService judgeNodeSecurityService;
    private final JudgeNodeHeartbeatService judgeNodeHeartbeatService;
    private final JudgeNodeAccessService judgeNodeAccessService;
    private final JudgeTaskLeaseManager judgeTaskLeaseManager;

    @Operation(summary = "校验判题节点凭证")
    @PostMapping("/tokens/validate")
    public Result<JudgeNodeTokenValidationVo> validateToken(@RequestBody ValidateJudgeNodeTokenRequest request) {
        return Result.success(judgeNodeSecurityService.validateToken(request));
    }

    @Operation(summary = "校验测试数据下载资格")
    @PostMapping("/tasks/access")
    public Result<Boolean> validateTaskAccess(
            @RequestBody JudgeTaskAccessRequest request,
            @RequestHeader(value = HeaderConstant.JUDGE_TOKEN, required = false) String judgeToken,
            @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization) {
        JudgeNodeIdentity identity = judgeNodeAccessService.resolveIdentity(judgeToken, authorization);
        judgeTaskLeaseManager.validateDownloadAccess(request.getSubmissionId(), request.getJudgeTaskId(),
                request.getAttemptId(), request.getProblemId(), identity);
        return Result.success(Boolean.TRUE);
    }

    @Operation(summary = "查询指定判题模式是否存在可用节点")
    @GetMapping("/nodes/capabilities/{judgeMode}/available")
    public Result<Boolean> hasActiveNodeForMode(@PathVariable("judgeMode") String judgeMode) {
        return Result.success(judgeNodeHeartbeatService.hasActiveNodeForMode(judgeMode));
    }
}
