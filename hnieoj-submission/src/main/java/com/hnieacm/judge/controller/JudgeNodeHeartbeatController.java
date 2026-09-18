package com.hnieacm.judge.controller;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.judge.dto.JudgeNodeHeartbeatRequest;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
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
 * @Description: 判题节点心跳接口
 */
@Hidden
@RestController
@RequestMapping("/judge/nodes")
@RequiredArgsConstructor
public class JudgeNodeHeartbeatController {

    private final JudgeNodeHeartbeatService judgeNodeHeartbeatService;

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody JudgeNodeHeartbeatRequest request,
                                  @RequestHeader(value = HeaderConstant.JUDGE_TOKEN, required = false) String judgeToken,
                                  @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization) {
        judgeNodeHeartbeatService.recordHeartbeat(judgeToken, authorization, request);
        return Result.success(null);
    }
}
