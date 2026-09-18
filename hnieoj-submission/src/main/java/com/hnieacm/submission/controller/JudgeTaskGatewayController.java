package com.hnieacm.submission.controller;

import com.hnieacm.common.constant.HeaderConstant;
import com.hnieacm.common.result.Result;
import com.hnieacm.judge.vo.JudgeNodeIdentity;
import com.hnieacm.submission.dto.JudgeTaskLeaseRequest;
import com.hnieacm.submission.service.JudgeNodeAccessService;
import com.hnieacm.submission.service.JudgeTaskClaimService;
import com.hnieacm.submission.vo.JudgeTaskClaimVo;
import com.hnieacm.submission.vo.JudgeTaskLeaseVo;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 内嵌判题任务网关：领取与续租，运行期只接受逐节点 Bearer
 */
@Hidden
@RestController
@RequestMapping("/judge/tasks")
@RequiredArgsConstructor
public class JudgeTaskGatewayController {

    private final JudgeNodeAccessService judgeNodeAccessService;
    private final JudgeTaskClaimService judgeTaskClaimService;

    @PostMapping("/claim")
    public Result<JudgeTaskClaimVo> claim(
            @RequestHeader(value = HeaderConstant.JUDGE_TOKEN, required = false) String judgeToken,
            @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization) {
        JudgeNodeIdentity identity = judgeNodeAccessService.resolveIdentity(judgeToken, authorization);
        return Result.success(judgeTaskClaimService.claim(identity));
    }

    @PostMapping("/{submissionId}/lease")
    public Result<JudgeTaskLeaseVo> renewLease(
            @PathVariable String submissionId,
            @RequestBody(required = false) JudgeTaskLeaseRequest request,
            @RequestHeader(value = HeaderConstant.JUDGE_TOKEN, required = false) String judgeToken,
            @RequestHeader(value = HeaderConstant.AUTHORIZATION, required = false) String authorization) {
        JudgeNodeIdentity identity = judgeNodeAccessService.resolveIdentity(judgeToken, authorization);
        return Result.success(judgeTaskClaimService.renew(submissionId, identity, request));
    }
}
