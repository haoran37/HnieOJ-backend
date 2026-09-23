package com.hnieacm.contest.controller;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.Result;
import com.hnieacm.common.result.ResultCode;
import com.hnieacm.contest.service.ContestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HnieOJ contributors
 * @Description: Internal contest problem access check for problem and submission services.
 */
@RestController
@RequestMapping("/internal/contests")
@RequiredArgsConstructor
public class InternalContestAccessController {

    private final ContestQueryService contestQueryService;

    @Value("${hnieoj.internal.token:}")
    private String internalToken;

    @GetMapping("/{contestId}/problems/{problemId}/access")
    public Result<Boolean> checkProblemAccess(@PathVariable Long contestId,
                                              @PathVariable Long problemId,
                                              @RequestParam String uid,
                                              @RequestHeader(value = HeaderConstant.INTERNAL_TOKEN, required = false) String token) {
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
            throw new BizException(ResultCode.FORBIDDEN, "禁止访问内部接口");
        }
        contestQueryService.checkProblemAccess(contestId, problemId, uid);
        return Result.success(true);
    }
}
