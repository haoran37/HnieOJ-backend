package com.hnieacm.judge.controller;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.util.JudgeNodeRequestContextUtils;
import com.hnieacm.judge.dto.JudgeNodeHeartbeatRequest;
import com.hnieacm.judge.service.JudgeNodeHeartbeatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ObjectMapper objectMapper;

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody byte[] body,
                                  @RequestHeader(value = HeaderConstant.JUDGE_TOKEN, required = false) String judgeToken,
                                  @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization,
                                  HttpServletRequest servletRequest) {
        judgeNodeHeartbeatService.recordHeartbeat(judgeToken, authorization,
                JudgeNodeRequestContextUtils.build(servletRequest, body), readRequest(body));
        return Result.success(null);
    }

    private JudgeNodeHeartbeatRequest readRequest(byte[] body) {
        try {
            return objectMapper.readValue(body, JudgeNodeHeartbeatRequest.class);
        } catch (Exception e) {
            throw new com.hnieacm.common.exception.BizException(
                    com.hnieacm.common.result.ResultCode.BAD_REQUEST, "心跳请求体格式不合法");
        }
    }
}
