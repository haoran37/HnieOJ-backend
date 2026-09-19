package com.hnieacm.submission.controller;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已退休的判题结果 HTTP 回传入口。
 *
 * <p>WSS 是唯一任务通道：该地址仅为兼容旧客户端保留，任何请求都明确拒绝，
 * 不解析、不验签、不落库，因此不构成签名 HTTP 任务旁路。</p>
 *
 * @author Codex
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/judge/submissions")
@RequiredArgsConstructor
public class JudgeSubmissionEventController {

    /** 已退休的签名 HTTP 结果回传路径：明确拒绝，不保留任务旁路。 */
    @PostMapping("/{submissionId}/events")
    public Result<Void> reportEvent(@PathVariable String submissionId,
                                    @RequestBody(required = false) byte[] body) {
        log.warn("Retired judge result HTTP callback invoked, submissionId: {}; rejecting", submissionId);
        throw new BizException(ResultCode.FORBIDDEN, "判题结果 HTTP 回传已退休，请使用 WSS 任务通道");
    }
}
