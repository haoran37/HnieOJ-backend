package com.hnieacm.judge.controller;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.judge.dto.JudgeNodeHeartbeatRequest;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
import com.hnieacm.judge.service.JudgeNodeSecurityService;
import com.hnieacm.judge.vo.JudgeTempTokenVo;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点心跳与稳定续期接口
 */
@Hidden
@RestController
@RequestMapping("/judge/nodes")
@RequiredArgsConstructor
public class JudgeNodeHeartbeatController {

    private final JudgeNodeHeartbeatService judgeNodeHeartbeatService;
    private final JudgeNodeSecurityService judgeNodeSecurityService;

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody JudgeNodeHeartbeatRequest request,
                                  @RequestHeader(value = HeaderConstant.JUDGE_TOKEN, required = false) String judgeToken,
                                  @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization) {
        judgeNodeHeartbeatService.recordHeartbeat(judgeToken, authorization, request);
        return Result.success(null);
    }

    @PostMapping("/token/renew")
    public Result<JudgeTempTokenVo> renewToken(
            @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization) {
        return Result.success(judgeNodeSecurityService.renewToken(authorization));
    }
}
