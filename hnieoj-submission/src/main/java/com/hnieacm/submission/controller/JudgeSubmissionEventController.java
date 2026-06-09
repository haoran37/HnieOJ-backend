package com.hnieacm.submission.controller;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.common.util.JudgeNodeRequestContextUtils;
import com.hnieacm.submission.dto.JudgeResultEventRequest;
import com.hnieacm.submission.service.JudgeNodeAccessService;
import com.hnieacm.submission.service.JudgeResultReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/06/07
 * @Description: 判题节点提交结果回传接口
 */
@Hidden
@RestController
@RequestMapping("/judge/submissions")
@RequiredArgsConstructor
public class JudgeSubmissionEventController {

    private final JudgeNodeAccessService judgeNodeAccessService;
    private final JudgeResultReportService judgeResultReportService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{submissionId}/events")
    public Result<Void> reportEvent(@PathVariable String submissionId,
                                    @RequestBody byte[] body,
                                    @RequestHeader(value = HeaderConstant.JUDGE_TOKEN, required = false) String judgeToken,
                                    @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization,
                                    HttpServletRequest servletRequest) {
        judgeNodeAccessService.checkAccess(judgeToken, authorization,
                JudgeNodeRequestContextUtils.build(servletRequest, body));
        JudgeResultEventRequest request = readRequest(body);
        judgeResultReportService.handleEvent(submissionId, request);
        return Result.success(null);
    }

    private JudgeResultEventRequest readRequest(byte[] body) {
        try {
            return objectMapper.readValue(body, JudgeResultEventRequest.class);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "结果回传请求体格式不合法");
        }
    }
}
